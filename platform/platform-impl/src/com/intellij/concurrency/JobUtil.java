/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.concurrency;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author cdr
 */

public class JobUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.concurrency.JobUtil");

  private static <T> boolean invokeConcurrentlyForAll(@NotNull final List<T> things,
                                                      @NotNull final Processor<T> thingProcessor,
                                                      boolean failFastOnAcquireReadAction) throws ProcessCanceledException {
    if (things.isEmpty()) {
      return true;
    }
    if (things.size() == 1) {
      T t = things.get(0);
      return thingProcessor.process(t);
    }

    final Job<String> job = new JobImpl<String>(Job.DEFAULT_PRIORITY, failFastOnAcquireReadAction);

    final int chunkSize = Math.max(1, things.size() / JobSchedulerImpl.CORES_COUNT / 100);
    for (int i = 0; i < things.size(); i += chunkSize) {
      // this job chunk is i..i+chunkSize-1
      final int finalI = i;
      job.addTask(new Runnable() {
        public void run() {
          try {
            for (int k = finalI; k < finalI + chunkSize && k < things.size(); k++) {
              T thing = things.get(k);
              if (!thingProcessor.process(thing)) {
                job.cancel();
                break;
              }
            }
          }
          catch (ProcessCanceledException e) {
            job.cancel();
            throw e;
          }
        }
      });
    }
    try {
      job.scheduleAndWaitForResults();
    }
    catch (RuntimeException e) {
      job.cancel();
      throw e;
    }
    catch (Throwable throwable) {
      job.cancel();
      LOG.error(throwable);
    }
    return !job.isCanceled();
  }

  /**
   * Schedules concurrent execution of #thingProcessor over each element of #things and waits for completion
   * With checkCanceled in each thread delegated to our current progress
   * @param things to process concurrently
   * @param thingProcessor to be invoked concurrently on each element from the collection
   * @param failFastOnAcquireReadAction if true, returns false when failed to acquire read action
   * @return false if tasks have been canceled
   *         or at least one processor returned false
   *         or threw exception
   *         or we were unable to start read action in at least one thread
   * @throws ProcessCanceledException if at least one task has thrown ProcessCanceledException
   */
  public static <T> boolean invokeConcurrentlyUnderMyProgress(@NotNull List<T> things,
                                                              @NotNull final Processor<T> thingProcessor,
                                                              boolean failFastOnAcquireReadAction) throws ProcessCanceledException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final ProgressWrapper wrapper = ProgressWrapper.wrap(indicator);
    return invokeConcurrentlyForAll(things, new Processor<T>() {
      public boolean process(final T t) {
        final boolean[] result = new boolean[1];
        ((ProgressManagerImpl)ProgressManager.getInstance()).executeProcessUnderProgress(new Runnable() {
          public void run() {
            result[0] = thingProcessor.process(t);
          }
        }, wrapper);
        return result[0];
      }
    }, failFastOnAcquireReadAction);
  }

  public static void invokeConcurrentlyOnAllCores(@NotNull final Runnable action) throws Throwable {
    Job<Void> job = new JobImpl<Void>(Job.DEFAULT_PRIORITY, false);
    for (int i=0; i< JobSchedulerImpl.CORES_COUNT; i++) {
      job.addTask(action);
    }
    job.scheduleAndWaitForResults();
  }

  public static Job<Void> submitToJobThread(@NotNull final Runnable action, int priority) {
    Job<Void> job = new JobImpl<Void>(priority, false);
    Callable<Void> callable = new Callable<Void>() {
      public Void call() throws Exception {
        try {
          action.run();
        }
        catch (ProcessCanceledException ignored) {
          // since it's the only task in the job, nothing to cancel
        }
        return null;
      }
    };
    job.addTask(callable);
    job.schedule();
    return job;
  }
}
