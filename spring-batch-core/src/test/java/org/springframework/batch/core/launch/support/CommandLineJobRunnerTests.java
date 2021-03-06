/*
 * Copyright 2006-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.batch.core.launch.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.converter.DefaultJobParametersConverter;
import org.springframework.batch.core.converter.JobParametersConverter;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.step.JobRepositorySupport;
import org.springframework.util.ClassUtils;

/**
 * @author Lucas Ward
 *
 */
public class CommandLineJobRunnerTests {

	private String jobPath = ClassUtils.addResourcePathToPackagePath(CommandLineJobRunnerTests.class,
			"launcher-with-environment.xml");

	private String jobName = "test-job";

	private String jobKey = "job.Key=myKey";

	private String scheduleDate = "schedule.Date=01/23/2008";

	private String vendorId = "vendor.id=33243243";

	private String[] args = new String[] { jobPath, jobName, jobKey, scheduleDate, vendorId };

	private InputStream stdin;

	@Before
	public void setUp() throws Exception {
		JobExecution jobExecution = new JobExecution(null, new Long(1), null, null);
		ExitStatus exitStatus = ExitStatus.COMPLETED;
		jobExecution.setExitStatus(exitStatus);
		StubJobLauncher.jobExecution = jobExecution;
		stdin = System.in;
		System.setIn(new InputStream() {
			@Override
			public int read() {
				return -1;
			}
		});
	}

	@After
	public void tearDown() throws Exception {
		System.setIn(stdin);
		StubJobLauncher.tearDown();
	}

	@Test
	public void testMain() throws Exception {
		CommandLineJobRunner.main(args);
		assertTrue("Injected JobParametersConverter not used instead of default", StubJobParametersConverter.called);
		assertEquals(0, StubSystemExiter.getStatus());
	}

	@Test
	public void testWithJobLocator() throws Exception {
		jobPath = ClassUtils.addResourcePathToPackagePath(CommandLineJobRunnerTests.class, "launcher-with-locator.xml");
		CommandLineJobRunner.main(new String[] { jobPath, jobName, jobKey });
		assertTrue("Injected JobParametersConverter not used instead of default", StubJobParametersConverter.called);
		assertEquals(0, StubSystemExiter.getStatus());
	}

	@Test
	public void testJobAlreadyRunning() throws Throwable {
		StubJobLauncher.throwExecutionRunningException = true;
		CommandLineJobRunner.main(args);
		assertEquals(1, StubSystemExiter.status);
	}

	@Test
	public void testInvalidArgs() throws Exception {
		String[] args = new String[] {};
		CommandLineJobRunner.presetSystemExiter(new StubSystemExiter());
		CommandLineJobRunner.main(args);
		assertEquals(1, StubSystemExiter.status);
		String errorMessage = CommandLineJobRunner.getErrorMessage();
		assertTrue("Wrong error message: " + errorMessage, errorMessage.contains("Config locations must not be null"));
	}

	@Test
	public void testWrongJobName() throws Exception {
		String[] args = new String[] { jobPath, "no-such-job" };
		CommandLineJobRunner.main(args);
		assertEquals(1, StubSystemExiter.status);
		String errorMessage = CommandLineJobRunner.getErrorMessage();
		assertTrue("Wrong error message: " + errorMessage, errorMessage
				.contains("No bean named 'no-such-job' is defined"));
	}

	@Test
	public void testWithNoParameters() throws Throwable {
		String[] args = new String[] { jobPath, jobName };
		CommandLineJobRunner.main(args);
		assertEquals(0, StubSystemExiter.status);
		assertEquals(new JobParameters(), StubJobLauncher.jobParameters);
	}

	@Test
	public void testWithInvalidStdin() throws Throwable {
		System.setIn(new InputStream() {
			@Override
			public int available() throws IOException {
				throw new IOException("Planned");
			}

			@Override
			public int read() {
				return -1;
			}
		});
		CommandLineJobRunner.main(new String[] { jobPath, jobName });
		assertEquals(0, StubSystemExiter.status);
		assertEquals(0, StubJobLauncher.jobParameters.getParameters().size());
	}

	@Test
	public void testWithStdinCommandLine() throws Throwable {
		System.setIn(new InputStream() {
			char[] input = (jobPath+"\n"+jobName+"\nfoo=bar\nspam=bucket").toCharArray();

			int index = 0;

			@Override
			public int available() {
				return input.length - index;
			}

			@Override
			public int read() {
				return index<input.length-1 ? (int) input[index++] : -1;
			}
		});
		CommandLineJobRunner.main(new String[0]);
		assertEquals(0, StubSystemExiter.status);
		assertEquals(2, StubJobLauncher.jobParameters.getParameters().size());
	}

	@Test
	public void testWithStdinParameters() throws Throwable {
		String[] args = new String[] { jobPath, jobName };
		System.setIn(new InputStream() {
			char[] input = ("foo=bar\nspam=bucket").toCharArray();

			int index = 0;

			@Override
			public int available() {
				return input.length - index;
			}

			@Override
			public int read() {
				return index<input.length-1 ? (int) input[index++] : -1;
			}
		});
		CommandLineJobRunner.main(args);
		assertEquals(0, StubSystemExiter.status);
		assertEquals(2, StubJobLauncher.jobParameters.getParameters().size());
	}

	@Test
	public void testWithInvalidParameters() throws Throwable {
		String[] args = new String[] { jobPath, jobName, "foo" };
		CommandLineJobRunner.main(args);
		assertEquals(1, StubSystemExiter.status);
		String errorMessage = CommandLineJobRunner.getErrorMessage();
		assertTrue("Wrong error message: " + errorMessage, errorMessage.contains("in the form name=value"));
	}

	@Test
	public void testStop() throws Throwable {
		String[] args = new String[] { jobPath, "-stop", jobName };
		StubJobExplorer.jobInstances = Arrays.asList(new JobInstance(3L, jobName));
		CommandLineJobRunner.main(args);
		assertEquals(0, StubSystemExiter.status);
	}

	@Test
	public void testStopFailed() throws Throwable {
		String[] args = new String[] { jobPath, "-stop", jobName };
		StubJobExplorer.jobInstances = Arrays.asList(new JobInstance(0L, jobName));
		CommandLineJobRunner.main(args);
		assertEquals(1, StubSystemExiter.status);
	}

	@Test
	public void testStopFailedAndRestarted() throws Throwable {
		String[] args = new String[] { jobPath, "-stop", jobName };
		StubJobExplorer.jobInstances = Arrays.asList(new JobInstance(5L, jobName));
		CommandLineJobRunner.main(args);
		assertEquals(0, StubSystemExiter.status);
	}

	@Test
	public void testStopRestarted() throws Throwable {
		String[] args = new String[] { jobPath, "-stop", jobName };
		JobInstance jobInstance = new JobInstance(3L, jobName);
		StubJobExplorer.jobInstances = Arrays.asList(jobInstance);
		CommandLineJobRunner.main(args);
		assertEquals(0, StubSystemExiter.status);
	}

	@Test
	public void testAbandon() throws Throwable {
		String[] args = new String[] { jobPath, "-abandon", jobName };
		StubJobExplorer.jobInstances = Arrays.asList(new JobInstance(2L, jobName));
		CommandLineJobRunner.main(args);
		assertEquals(0, StubSystemExiter.status);
	}

	@Test
	public void testAbandonRunning() throws Throwable {
		String[] args = new String[] { jobPath, "-abandon", jobName };
		StubJobExplorer.jobInstances = Arrays.asList(new JobInstance(3L, jobName));
		CommandLineJobRunner.main(args);
		assertEquals(1, StubSystemExiter.status);
	}

	@Test
	public void testAbandonAbandoned() throws Throwable {
		String[] args = new String[] { jobPath, "-abandon", jobName };
		StubJobExplorer.jobInstances = Arrays.asList(new JobInstance(4L, jobName));
		CommandLineJobRunner.main(args);
		assertEquals(1, StubSystemExiter.status);
	}

	@Test
	public void testRestart() throws Throwable {
		String[] args = new String[] { jobPath, "-restart", jobName };
		JobParameters jobParameters = new JobParametersBuilder().addString("foo", "bar").toJobParameters();
		JobInstance jobInstance = new JobInstance(0L, jobName);
		StubJobExplorer.jobInstances = Arrays.asList(jobInstance);
		StubJobExplorer.jobParameters = jobParameters;
		CommandLineJobRunner.main(args);
		assertEquals(0, StubSystemExiter.status);
		assertEquals(jobParameters, StubJobLauncher.jobParameters);
		StubJobExplorer.jobParameters = new JobParameters();
	}

	@Test
	public void testRestartExecution() throws Throwable {
		String[] args = new String[] { jobPath, "-restart", "11" };
		JobParameters jobParameters = new JobParametersBuilder().addString("foo", "bar").toJobParameters();
		JobExecution jobExecution = new JobExecution(new JobInstance(0L, jobName), 11L, jobParameters, null);
		jobExecution.setStatus(BatchStatus.FAILED);
		StubJobExplorer.jobExecution = jobExecution;
		CommandLineJobRunner.main(args);
		assertEquals(0, StubSystemExiter.status);
		assertEquals(jobParameters, StubJobLauncher.jobParameters);
	}

	@Test
	public void testRestartExecutionNotFailed() throws Throwable {
		String[] args = new String[] { jobPath, "-restart", "11" };
		JobParameters jobParameters = new JobParametersBuilder().addString("foo", "bar").toJobParameters();
		JobExecution jobExecution = new JobExecution(new JobInstance(0L, jobName), 11L, jobParameters, null);
		jobExecution.setStatus(BatchStatus.COMPLETED);
		StubJobExplorer.jobExecution = jobExecution;
		CommandLineJobRunner.main(args);
		assertEquals(1, StubSystemExiter.status);
		assertEquals(null, StubJobLauncher.jobParameters);
	}

	@Test
	public void testRestartNotFailed() throws Throwable {
		String[] args = new String[] { jobPath, "-restart", jobName };
		StubJobExplorer.jobInstances = Arrays.asList(new JobInstance(123L, jobName));
		CommandLineJobRunner.main(args);
		assertEquals(1, StubSystemExiter.status);
		String errorMessage = CommandLineJobRunner.getErrorMessage();
		assertTrue("Wrong error message: " + errorMessage, errorMessage
				.contains("No failed or stopped execution found"));
	}

	@Test
	public void testNext() throws Throwable {
		String[] args = new String[] { jobPath, "-next", jobName, "bar=foo" };
		JobParameters jobParameters = new JobParametersBuilder().addString("foo", "bar").addString("bar", "foo")
				.toJobParameters();
		StubJobExplorer.jobInstances = Arrays.asList(new JobInstance(2L, jobName));
		CommandLineJobRunner.main(args);
		assertEquals(0, StubSystemExiter.status);
		jobParameters = new JobParametersBuilder().addString("foo", "spam").addString("bar", "foo").toJobParameters();
		assertEquals(jobParameters, StubJobLauncher.jobParameters);
	}

	@Test
	public void testNextFirstInSequence() throws Throwable {
		String[] args = new String[] { jobPath, "-next", jobName };
		StubJobExplorer.jobInstances = new ArrayList<JobInstance>();
		CommandLineJobRunner.main(args);
		assertEquals(0, StubSystemExiter.status);
		JobParameters jobParameters = new JobParametersBuilder().addString("foo", "spam").toJobParameters();
		assertEquals(jobParameters, StubJobLauncher.jobParameters);
	}

	@Test
	public void testNextWithNoParameters() throws Exception {
		jobPath = ClassUtils.addResourcePathToPackagePath(CommandLineJobRunnerTests.class, "launcher-with-locator.xml");
		CommandLineJobRunner.main(new String[] { jobPath, "-next", "test-job2", jobKey });
		assertEquals(1, StubSystemExiter.getStatus());
		String errorMessage = CommandLineJobRunner.getErrorMessage();
		assertTrue("Wrong error message: " + errorMessage, errorMessage
				.contains(" No job parameters incrementer found"));
	}

	@Test
	public void testDestroyCallback() throws Throwable {
		String[] args = new String[] { jobPath, jobName };
		CommandLineJobRunner.main(args);
		assertTrue(StubJobLauncher.destroyed);
	}

	public static class StubSystemExiter implements SystemExiter {

		private static int status;

		@Override
		public void exit(int status) {
			StubSystemExiter.status = status;
		}

		public static int getStatus() {
			return status;
		}
	}

	public static class StubJobLauncher implements JobLauncher {

		public static JobExecution jobExecution;

		public static boolean throwExecutionRunningException = false;

		public static JobParameters jobParameters;

		private static boolean destroyed = false;

		@Override
		public JobExecution run(Job job, JobParameters jobParameters) throws JobExecutionAlreadyRunningException {

			StubJobLauncher.jobParameters = jobParameters;

			if (throwExecutionRunningException) {
				throw new JobExecutionAlreadyRunningException("");
			}

			return jobExecution;
		}

		public void destroy() {
			destroyed = true;
		}

		public static void tearDown() {
			jobExecution = null;
			throwExecutionRunningException = false;
			jobParameters = null;
			destroyed = false;
		}
	}

	public static class StubJobRepository extends JobRepositorySupport {
	}

	public static class StubJobExplorer implements JobExplorer {

		static List<JobInstance> jobInstances = new ArrayList<JobInstance>();

		static JobExecution jobExecution;

		static JobParameters jobParameters = new JobParameters();

		@Override
		public Set<JobExecution> findRunningJobExecutions(String jobName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public JobExecution getJobExecution(Long executionId) {
			if (jobExecution != null) {
				return jobExecution;
			}
			throw new UnsupportedOperationException();
		}

		@Override
		public List<JobExecution> getJobExecutions(JobInstance jobInstance) {
			if (jobInstance.getId() == 0) {
				return Arrays.asList(createJobExecution(jobInstance, BatchStatus.FAILED));
			}
			if (jobInstance.getId() == 1) {
				return null;
			}
			if (jobInstance.getId() == 2) {
				return Arrays.asList(createJobExecution(jobInstance, BatchStatus.STOPPED));
			}
			if (jobInstance.getId() == 3) {
				return Arrays.asList(createJobExecution(jobInstance, BatchStatus.STARTED));
			}
			if (jobInstance.getId() == 4) {
				return Arrays.asList(createJobExecution(jobInstance, BatchStatus.ABANDONED));
			}
			if (jobInstance.getId() == 5) {
				return Arrays.asList(createJobExecution(jobInstance, BatchStatus.STARTED), createJobExecution(
						jobInstance, BatchStatus.FAILED));
			}
			return Arrays.asList(createJobExecution(jobInstance, BatchStatus.COMPLETED));
		}

		private JobExecution createJobExecution(JobInstance jobInstance, BatchStatus status) {
			JobExecution jobExecution = new JobExecution(jobInstance, 1L, jobParameters, null);
			jobExecution.setStatus(status);
			jobExecution.setStartTime(new Date());
			if (status != BatchStatus.STARTED) {
				jobExecution.setEndTime(new Date());
			}
			return jobExecution;
		}

		@Override
		public JobInstance getJobInstance(Long instanceId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<JobInstance> getJobInstances(String jobName, int start, int count) {
			if (jobInstances == null) {
				return new ArrayList<JobInstance>();
			}
			List<JobInstance> result = jobInstances;
			jobInstances = null;
			return result;
		}

		@Override
		public StepExecution getStepExecution(Long jobExecutionId, Long stepExecutionId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<String> getJobNames() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getJobInstanceCount(String jobName)
				throws NoSuchJobException {
			int count = 0;

			for (JobInstance jobInstance : jobInstances) {
				if(jobInstance.getJobName().equals(jobName)) {
					count++;
				}
			}

			if(count == 0) {
				throw new NoSuchJobException("Unable to find job instances for " + jobName);
			} else {
				return count;
			}
		}

	}

	public static class StubJobParametersConverter implements JobParametersConverter {

		JobParametersConverter delegate = new DefaultJobParametersConverter();

		static boolean called = false;

		@Override
		public JobParameters getJobParameters(Properties properties) {
			called = true;
			return delegate.getJobParameters(properties);
		}

		@Override
		public Properties getProperties(JobParameters params) {
			throw new UnsupportedOperationException();
		}

	}

}
