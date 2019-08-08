// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Future;

/**
 * Invitation-only service for running short-lived computing-intensive IO-free tasks on all available CPU cores.
 * DO NOT USE for your tasks, IO-bound or long tasks, there are
 * {@link com.intellij.openapi.application.Application#executeOnPooledThread},
 * {@link com.intellij.execution.process.ProcessIOExecutorService} and {@link com.intellij.util.concurrency.NonUrgentExecutor} for that.
 */
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
    Application app = ApplicationManager.getApplication();
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
  public <T> boolean invokeConcurrentlyUnderProgress(@NotNull List<? extends T> things,
                                                     ProgressIndicator progress,
                                                     boolean failFastOnAcquireReadAction,
                                                     @NotNull Processor<? super T> thingProcessor) throws ProcessCanceledException {
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
  @NotNull
  public abstract Job<Void> submitToJobThread(@NotNull final Runnable action, @Nullable Consumer<? super Future<?>> onDoneCallback);
}
