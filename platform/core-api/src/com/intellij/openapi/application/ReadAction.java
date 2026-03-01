// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.RunnableCallable;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

/**
 * See <a href="https://plugins.jetbrains.com/docs/intellij/threading-model.html">Threading Model</a>
 *
 * @param <T> Result type.
 * @see WriteAction
 * @see CoroutinesKt#readAction
 */
public abstract class ReadAction<T> extends BaseActionRunnable<T> {

  /**
   * @deprecated use {@link #runBlocking(ThrowableRunnable)} or {@link #computeBlocking(ThrowableComputable)} instead
   */
  @Deprecated
  @Override
  public @NotNull RunResult<T> execute() {
    final RunResult<T> result = new RunResult<>(this);
    return computeBlocking(() -> result.run());
  }

  /**
   * @deprecated use {@link #runBlocking(ThrowableRunnable)} or {@link #computeBlocking(ThrowableComputable)} instead
   */
  @Deprecated
  @Override
  protected abstract void run(@NotNull Result<? super T> result) throws Throwable;

  /**
   * @see ReadAction#nonBlocking for background processing without suspend
   * @see NonBlockingReadAction#executeSynchronously for synchronous execution in background threads
   * @see CoroutinesKt#readAction for suspend contexts
   * @see #runBlocking(ThrowableRunnable) for explicitly non-cancellable read actions, avoid using in background threads
   *
   * @deprecated use {@link ReadAction#nonBlocking(Callable)} or {@link #computeBlocking(ThrowableComputable)} (for explicitly non-cancellable read actions)
   */
  @Deprecated
  @RequiresBlockingContext
  public static <E extends Throwable> void run(@NotNull ThrowableRunnable<E> action) throws E {
    runBlocking(action);
  }

  /**
   * @see ReadAction#nonBlocking for background processing without suspend
   * @see NonBlockingReadAction#executeSynchronously for synchronous execution in background threads
   * @see CoroutinesKt#readAction for suspend contexts
   * @see #computeBlocking(ThrowableComputable) for explicitly non-cancellable read actions, avoid using in background threads
   *
   * @deprecated use {@link ReadAction#nonBlocking(Callable)} or {@link #computeBlocking(ThrowableComputable)} (for explicitly non-cancellable read actions)
   */
  @Deprecated
  @RequiresBlockingContext
  public static <T, E extends Throwable> T compute(@NotNull ThrowableComputable<T, E> action) throws E {
    return computeBlocking(action);
  }

  /**
   * Runs the specified computation in a blocking read action (as opposed to {@link NonBlockingReadAction}).
   * Can be called from any thread. Do not get canceled if a write action is pending, executed at most once.
   * <p>
   * Avoid usage in background threads as it will likely cause UI freezes. Use it only under modal progress or from EDT.
   * <p>
   * The computation is executed immediately if no write action is currently running or the write action is running on the current thread.
   * Otherwise, the action is <b>blocked</b> until the currently running write action completes.
   *
   * @see ReadAction#nonBlocking for background processing without suspend
   * @see NonBlockingReadAction#executeSynchronously for synchronous execution in background threads
   * @see CoroutinesKt#readAction for suspend contexts
   */
  @RequiresBlockingContext
  public static <T, E extends Throwable> T computeBlocking(@NotNull ThrowableComputable<T, E> action) throws E {
    return ApplicationManager.getApplication().runReadAction(action);
  }

  /**
   * Runs the specified computation in a blocking read action (as opposed to {@link com.intellij.openapi.application.NonBlockingReadAction}).
   * Can be called from any thread. Do not get canceled if a write action is pending, executed at most once.
   * <p>
   * Avoid usage in background threads as it will likely cause UI freezes.
   * Use it only under modal progress or from EDT.
   * <p>
   * The computation is executed immediately if no write action is currently running or the write action is running on the current thread.
   * Otherwise, the action is **blocked** until the currently running write action completes.
   *
   * @see ReadAction#nonBlocking for background processing without suspend
   * @see NonBlockingReadAction#executeSynchronously for synchronous execution in background threads
   * @see CoroutinesKt#readAction for suspend contexts
   */
  @SuppressWarnings("SSBasedInspection") // to not replace application call to ReadAction.compute
  @RequiresBlockingContext
  public static <E extends Throwable> void runBlocking(@NotNull ThrowableRunnable<E> action) throws E {
    ApplicationManager.getApplication().runReadAction((ThrowableComputable<Object, E>)() -> {
      action.run();
      return null;
    });
  }

  /**
   * Create an {@link NonBlockingReadAction} builder to run the given Runnable in a non-blocking read action on a background thread.
   *
   * @deprecated reorganize the code so that this method is not used at all (better),
   * or pass explicit {@code Callable<Void>} to {@link #nonBlocking(Callable)}, if this method is really needed
   * <p>
   * The {@code task} might be executed several times, it may be canceled on write action,
   * and then restarted again once write action is finished.
   * If the client doesn't expect a result, then the task is mutating some outer state,
   * which greatly lowers its probability of being idempotent,
   * which in turn may cause delayed bugs in unrelated places and races.
   *
   * @see NonBlockingReadAction#executeSynchronously() for synchronous execution in background threads
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull NonBlockingReadAction<Void> nonBlocking(@NotNull Runnable task) {
    return nonBlocking(new RunnableCallable(task));
  }

  /**
   * Create an {@link NonBlockingReadAction} builder to run the given Callable in a non-blocking Read action on a background thread.
   *
   * @see CoroutinesKt#readAction
   * @see CoroutinesKt#constrainedReadAction
   */
  @Contract(pure = true)
  public static @NotNull <T> NonBlockingReadAction<T> nonBlocking(@NotNull Callable<? extends T> task) {
    return AsyncExecutionService.getService().buildNonBlockingReadAction(task);
  }

  /**
   * Runs the specified computation in a cancellable read action with a single attempt.
   * <p/>
   * If there is running or a requested write action, this method throws {@link CannotReadException},
   * otherwise the computation is executed immediately in the current thread.
   * If a write action is requested during the computation, the computation becomes canceled
   * (i.e., {@link ProgressManager#checkCanceled()} starts to throw inside the computation),
   * and this method throws {@link CannotReadException}.
   *
   * @throws CannotReadException if the read action cannot be started,
   *                             or if it was canceled by a requested write action during its execution
   */
  @RequiresBackgroundThread
  public static <T, E extends Throwable> T computeCancellable(
    @RequiresReadLock ThrowableComputable<T, E> computable
  ) throws E, CannotReadException {
    return ApplicationManager.getApplication().getService(ReadWriteActionSupport.class).computeCancellable(computable);
  }

  public static final class CannotReadException extends ProcessCanceledException {

    @Internal
    public CannotReadException() {
      // Still public constructor because it is used in AsciiDoc plugin
      super();
    }

    @Internal
    public static @NotNull Runnable jobCancellation(@NotNull Job job) {
      return () -> job.cancel(new CannotReadException());
    }
  }
}
