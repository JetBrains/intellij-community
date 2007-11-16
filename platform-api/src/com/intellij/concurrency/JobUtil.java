package com.intellij.concurrency;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author cdr
 */

public class JobUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.concurrency.JobUtil");

  public static <T> boolean invokeConcurrentlyForAll(@NotNull T[] things, @NotNull final Processor<T> thingProcessor, @NotNull @NonNls String jobName) throws ProcessCanceledException {
    return invokeConcurrentlyForAll(Arrays.asList(things), thingProcessor, jobName);
  }

  public static <T> boolean invokeConcurrentlyForAll(@NotNull Collection<T> things, @NotNull final Processor<T> thingProcessor, @NotNull @NonNls String jobName) throws ProcessCanceledException {
    final Job<?> job = JobScheduler.getInstance().createJob(jobName, Job.DEFAULT_PRIORITY);

    for (final T thing : things) {
      job.addTask(new Runnable(){
        public void run() {
          if (!thingProcessor.process(thing)) {
            job.cancel();
          }
        }
      });
    }
    try {
      job.scheduleAndWaitForResults();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable throwable) {
      LOG.error(throwable);
    }
    return !job.isCanceled();
  }
}
