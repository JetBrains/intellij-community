/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * @author cdr
 */
public class JobLauncherImpl extends JobLauncher {
  private static <T> boolean invokeConcurrentlyForAll(@NotNull final List<? extends T> things,
                                                      boolean runInReadAction,
                                                      boolean failFastOnAcquireReadAction,
                                                      @NotNull final Processor<T> thingProcessor,
                                                      final ProgressWrapper wrapper) throws ProcessCanceledException {
    final JobImpl<String> job = new JobImpl<String>(Job.DEFAULT_PRIORITY, failFastOnAcquireReadAction);

    final int chunkSize = Math.max(1, things.size() / Math.max(1, JobSchedulerImpl.CORES_COUNT / 2));
    for (int i = 0; i < things.size(); i += chunkSize) {
      // this job chunk is i..i+chunkSize-1
      final int finalI = i;
      job.addTask(new Runnable() {
        @Override
        public void run() {
          ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
            @Override
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
          }, wrapper);
        }
      });
    }
    try {
      job.scheduleAndWaitForResults(runInReadAction);
    }
    catch (RuntimeException e) {
      job.cancel();
      throw e;
    }
    catch (Throwable throwable) {
      job.cancel();
      throw new ProcessCanceledException(throwable);
    }
    return !job.isCanceled();
  }

  @Override
  public <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T>things,
                                                     ProgressIndicator progress,
                                                     boolean failFastOnAcquireReadAction,
                                                     @NotNull final Processor<T> thingProcessor) throws ProcessCanceledException {
    return invokeConcurrentlyUnderProgress(things, progress, ApplicationManager.getApplication().isReadAccessAllowed(),
                                           failFastOnAcquireReadAction, thingProcessor);
  }

  @Override
  public <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T> things,
                                                     ProgressIndicator progress,
                                                     boolean runInReadAction,
                                                     boolean failFastOnAcquireReadAction,
                                                     @NotNull Processor<T> thingProcessor) {
    if (things.isEmpty()) {
      return true;
    }
    if (things.size() == 1) {
      T t = things.get(0);
      return thingProcessor.process(t);
    }

    // can be already wrapped
    final ProgressWrapper wrapper = progress instanceof ProgressWrapper ? (ProgressWrapper)progress : ProgressWrapper.wrap(progress);
    return invokeConcurrentlyForAll(things, runInReadAction, failFastOnAcquireReadAction, thingProcessor, wrapper);
  }

  // This implementation is not really async
  @NotNull
  @Override
  public <T> AsyncFutureResult<Boolean> invokeConcurrentlyUnderProgressAsync(@NotNull List<? extends T> things,
                                                                             ProgressIndicator progress,
                                                                             boolean failFastOnAcquireReadAction,
                                                                             @NotNull Processor<T> thingProcessor) {
    final AsyncFutureResult<Boolean> asyncFutureResult = AsyncFutureFactory.getInstance().createAsyncFutureResult();
    try {
      final boolean result = invokeConcurrentlyUnderProgress(things, progress, failFastOnAcquireReadAction, thingProcessor);
      asyncFutureResult.set(result);
    }
    catch (Throwable t) {
      asyncFutureResult.setException(t);
    }
    return asyncFutureResult;
  }

  @NotNull
  @Override
  public Job<Void> submitToJobThread(int priority, @NotNull final Runnable action, Consumer<Future> onDoneCallback) {
    final JobImpl<Void> job = new JobImpl<Void>(priority, false);
    Callable<Void> callable = new Callable<Void>() {
      @Override
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
    job.addTask(callable, onDoneCallback);
    job.schedule();
    return job;
  }
}
