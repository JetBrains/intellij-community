// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Invitation-only service for running short-lived computing-intensive IO-free tasks on all available CPU cores.
 * DO NOT USE for your tasks, IO-bound or long tasks, there are
 * {@link com.intellij.openapi.application.Application#executeOnPooledThread},
 * {@link com.intellij.execution.process.ProcessIOExecutorService} and {@link com.intellij.util.concurrency.NonUrgentExecutor} for that.
 */
@ApiStatus.Internal
public abstract class JobLauncher {
  public static JobLauncher getInstance() {
    return ApplicationManager.getApplication().getService(JobLauncher.class);
  }

  /// Schedules concurrent execution of `thingProcessor` over each element of `things` and waits for completion.
  /// Note: When the `thingProcessor` throws an exception or returns `false`  or the current indicator is canceled,
  /// the method is finished with `false` as soon as possible,
  /// which means some workers might still be in flight to completion. On the other hand, when the method returns `true`,
  /// it's guaranteed that the whole list was processed and all tasks completed.
  /// Must be called under the [com.intellij.openapi.progress.ProgressIndicator].
  ///
  /// @param things                      data to process concurrently
  /// @param thingProcessor              to be invoked concurrently on each element from the collection
  /// @return false if tasks have been canceled,
  ///         or at least one processor returned false,
  ///         or threw an exception,
  ///         or we were unable to start read action in at least one thread
  /// @throws ProcessCanceledException if at least one task has thrown ProcessCanceledException
  //@RequiresBackgroundThread
  public <T> boolean invokeConcurrentlyUnderContextProgress(@NotNull List<? extends T> things,
                                                            @NotNull Processor<? super T> thingProcessor) throws ProcessCanceledException {
    return processConcurrentlyAsync(things, thingProcessor, ()->{});
  }

  /// Process each element in `things` with `thingProcessor` in a background in an async manner,
  /// while running `runnable` synchronously.
  /// All processing is finished when the method returns, unless PCE is thrown, in which case there are no guarantees
  /// Must be called under the [com.intellij.openapi.progress.ProgressIndicator].
  //@RequiresBackgroundThread
  public abstract <T> boolean processConcurrentlyAsync(@NotNull List<? extends T> things,
                                              @NotNull Processor<? super T> thingProcessor,
                                              @NotNull Runnable runnable) throws ProcessCanceledException;
}
