/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author cdr
 */

public class JobUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.concurrency.JobUtil");

  /**
   * @param things to process concurrently
   * @param thingProcessor to be invoked concurrently on each element from the collection
   * @param jobName the name of the job that invokes all the tasks
   * @return false if tasks have been canceled
   * @throws ProcessCanceledException if at least one task has thrown ProcessCanceledException
   */
  public static <T> boolean invokeConcurrentlyForAll(@NotNull Collection<T> things, @NotNull final Processor<T> thingProcessor, @NotNull @NonNls String jobName) throws ProcessCanceledException {
    if (things.isEmpty()) {
      return true;
    }
    if (things.size() == 1) {
      T t = things.iterator().next();
      return thingProcessor.process(t);
    }

    final Job<String> job = JobScheduler.getInstance().createJob(jobName, Job.DEFAULT_PRIORITY);

    for (final T thing : things) {
      //noinspection HardCodedStringLiteral
      job.addTask(new Runnable(){
        public void run() {
          if (!thingProcessor.process(thing)) {
            job.cancel();
          }
        }
      }, "done");
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

  // execute in multiple threads, with checkCanceled in each delegated to our current progress
  public static <T> boolean invokeConcurrentlyUnderMyProgress(@NotNull Collection<T> things, @NotNull final Processor<T> thingProcessor, @NotNull @NonNls String jobName) throws ProcessCanceledException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    return invokeConcurrentlyForAll(things, new Processor<T>() {
      public boolean process(final T t) {
        final boolean[] result = new boolean[1];
        ProgressManager.getInstance().runProcess(new Runnable() {
          public void run() {
            result[0] = thingProcessor.process(t);
          }
        }, ProgressWrapper.wrap(indicator));
        return result[0];
      }
    }, jobName);
  }

}
