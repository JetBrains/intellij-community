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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Future;

public abstract class JobLauncher {
  public static JobLauncher getInstance() {
    return ServiceManager.getService(JobLauncher.class);
  }

  /**
   * Schedules concurrent execution of #thingProcessor over each element of #things and waits for completion
   * With checkCanceled in each thread delegated to our current progress
   *
   * @param things                      data to process concurrently
   * @param progress                    progress indicator
   * @param failFastOnAcquireReadAction if true, returns false when failed to acquire read action
   * @param thingProcessor              to be invoked concurrently on each element from the collection
   * @return false if tasks have been canceled,
   *         or at least one processor returned false,
   *         or threw an exception,
   *         or we were unable to start read action in at least one thread
   * @throws ProcessCanceledException if at least one task has thrown ProcessCanceledException
   */
  public abstract <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T> things,
                                                              ProgressIndicator progress,
                                                              boolean failFastOnAcquireReadAction,
                                                              @NotNull Processor<T> thingProcessor) throws ProcessCanceledException;

  public abstract <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T> things,
                                                              ProgressIndicator progress,
                                                              boolean runInReadAction,
                                                              boolean failFastOnAcquireReadAction,
                                                              @NotNull Processor<T> thingProcessor) throws ProcessCanceledException;

  @NotNull
  public abstract <T> AsyncFuture<Boolean> invokeConcurrentlyUnderProgressAsync(@NotNull List<? extends T> things,
                                                                                ProgressIndicator progress,
                                                                                boolean failFastOnAcquireReadAction,
                                                                                @NotNull Processor<T> thingProcessor);


  @NotNull
  public abstract Job<Void> submitToJobThread(int priority, @NotNull final Runnable action, @Nullable Consumer<Future> onDoneCallback);

  @NotNull
  public Job<Void> submitToJobThread(int priority, @NotNull final Runnable action) {
    return submitToJobThread(priority, action, null);
  }
}
