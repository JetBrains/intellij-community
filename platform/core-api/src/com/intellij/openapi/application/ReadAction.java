// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.RunnableCallable;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

/**
 * See <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html">General Threading Rules</a>
 *
 * @param <T> Result type.
 * @see WriteAction
 */
public abstract class ReadAction<T> extends BaseActionRunnable<T> {

  /**
   * @deprecated use {@link #run(ThrowableRunnable)} or {@link #compute(ThrowableComputable)} instead
   */
  @Deprecated
  @NotNull
  @Override
  public RunResult<T> execute() {
    final RunResult<T> result = new RunResult<>(this);
    return compute(() -> result.run());
  }

  /**
   * @deprecated use {@link #run(ThrowableRunnable)} or {@link #compute(ThrowableComputable)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static AccessToken start() {
    return ApplicationManager.getApplication().acquireReadActionLock();
  }

  /**
   * @deprecated use {@link #run(ThrowableRunnable)} or {@link #compute(ThrowableComputable)} instead
   */
  @Deprecated
  @Override
  protected abstract void run(@NotNull Result<? super T> result) throws Throwable;

  /**
   * @see Application#runReadAction(Runnable)
   */
  public static <E extends Throwable> void run(@NotNull ThrowableRunnable<E> action) throws E {
    compute(() -> {
      action.run();
      return null;
    });
  }

  /**
   * @see Application#runReadAction(ThrowableComputable)
   */
  public static <T, E extends Throwable> T compute(@NotNull ThrowableComputable<T, E> action) throws E {
    return ApplicationManager.getApplication().runReadAction(action);
  }

  /**
   * Create an {@link NonBlockingReadAction} builder to run the given Runnable in non-blocking read action on a background thread.
   */
  @NotNull
  @Contract(pure = true)
  public static NonBlockingReadAction<Void> nonBlocking(@NotNull Runnable task) {
    return nonBlocking(new RunnableCallable(task));
  }

  /**
   * Create an {@link NonBlockingReadAction} builder to run the given Callable in a non-blocking read action on a background thread.
   */
  @NotNull
  @Contract(pure = true)
  public static <T> NonBlockingReadAction<T> nonBlocking(@NotNull Callable<? extends T> task) {
    return AsyncExecutionService.getService().buildNonBlockingReadAction(task);
  }

  /**
   * Runs the specified computation in a cancellable read action.
   * <p/>
   * If there is a running or a requested write action, this method throws {@link CannotReadException},
   * otherwise the computation is executed immediately in the current thread.
   * If a write action is requested during the computation, the computation becomes cancelled
   * (i.e., {@link ProgressManager#checkCanceled()} starts to throw inside the computation),
   * and this method throws {@link CannotReadException}.
   *
   * @throws CannotReadException if the read action cannot be started,
   *                             or if it was cancelled by a requested write action during its execution
   */
  @Experimental
  @RequiresBackgroundThread
  public static <T, E extends Throwable> T computeCancellable(
    @RequiresReadLock ThrowableComputable<T, E> computable
  ) throws E, CannotReadException {
    return ApplicationManager.getApplication().getService(ReadActionSupport.class).computeCancellable(computable);
  }

  public static final class CannotReadException extends ProcessCanceledException {

    @Internal
    public CannotReadException() { super(); }
  }
}
