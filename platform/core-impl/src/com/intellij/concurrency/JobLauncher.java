// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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

  /**
   * Schedules concurrent execution of {@code thingProcessor} over each element of {@code things} and waits for completion
   * with checkCanceled in each thread delegated to the {@code progress} (or the current global progress if null).
   * Note: When the {@code thingProcessor} throws an exception or returns {@code false}  or the current indicator is canceled,
   * the method is finished with {@code false} as soon as possible,
   * which means some workers might still be in flight to completion. On the other hand, when the method returns {@code true},
   * it's guaranteed that the whole list was processed and all tasks completed.
   *
   * @param things                      data to process concurrently
   * @param progress                    progress indicator
   * @param thingProcessor              to be invoked concurrently on each element from the collection
   * @return false if tasks have been canceled,
   *         or at least one processor returned false,
   *         or threw an exception,
   *         or we were unable to start read action in at least one thread
   * @throws ProcessCanceledException if at least one task has thrown ProcessCanceledException
   */
  public <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T> things,
                                                     ProgressIndicator progress,
                                                     @NotNull Processor<? super T> thingProcessor) throws ProcessCanceledException {
    ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();
    return invokeConcurrentlyUnderProgress(things, progress, app.isReadAccessAllowed(), app.isInImpatientReader(), thingProcessor);
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
   * @deprecated use {@link #invokeConcurrentlyUnderProgress(List, ProgressIndicator, Processor)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T> things,
                                                     ProgressIndicator progress,
                                                     boolean failFastOnAcquireReadAction,
                                                     @NotNull Processor<? super T> thingProcessor) throws ProcessCanceledException {
    PluginException.reportDeprecatedUsage("invokeConcurrentlyUnderProgress", "do not use");
    return invokeConcurrentlyUnderProgress(things, progress, ApplicationManager.getApplication().isReadAccessAllowed(),
                                           failFastOnAcquireReadAction, thingProcessor);
  }


  public abstract <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T> things,
                                                              ProgressIndicator progress,
                                                              boolean runInReadAction,
                                                              boolean failFastOnAcquireReadAction,
                                                              @NotNull Processor<? super T> thingProcessor) throws ProcessCanceledException;

  /**
   * NEVER EVER submit runnable which can lock itself for indeterminate amount of time.
   * This will cause deadlock since this thread pool is an easily exhaustible resource.
   * Use {@link com.intellij.openapi.application.Application#executeOnPooledThread(Runnable)} instead
   */
  public abstract @NotNull Job<Void> submitToJobThread(final @NotNull Runnable action, @Nullable Consumer<? super Future<?>> onDoneCallback);

  @ApiStatus.Internal
  @NotNull
  public abstract <T> BooleanSupplier processQueueAsync(@NotNull BlockingQueue<@NotNull T> things,
                                                        @NotNull ProgressIndicator progress,
                                                        @NotNull T tombStone,
                                                        @NotNull Processor<? super T> thingProcessor) throws ProcessCanceledException;

}
