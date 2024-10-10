// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.CoroutinesKt;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Most methods in this class are used to equip long background processes which take read actions with a special listener
 * that fires when a write action is about to begin, and cancels corresponding progress indicators to avoid blocking the UI.
 * These processes should be ready to get {@link ProcessCanceledException} at any moment.
 * Processes may want to react on cancellation event by restarting the activity, see
 * {@link ReadTask#onCanceled(ProgressIndicator)} for that.
 *
 * @author gregsh
 */
@Obsolete
public final class ProgressIndicatorUtils {
  private static final Logger LOG = Logger.getInstance(ProgressIndicatorUtils.class);

  private static final int MAX_REJECTED_EXECUTIONS_BEFORE_CANCELLATION = 16;


  /**
   * @deprecated It does not make much sense to cancel a BG task,
   * which does not hold a read lock, when a write action is pending.
   * Use {@link CoroutinesKt#readAction} to run a read action,
   * which is cancelled on write. Don't cancel arbitrary tasks on write.
   */
  @Deprecated
  public static @NotNull ProgressIndicator forceWriteActionPriority(@NotNull ProgressIndicator progress,
                                                                    @NotNull Disposable parentDisposable) {
    ApplicationManager.getApplication().addApplicationListener(new ApplicationListener() {
        @Override
        public void beforeWriteActionStart(@NotNull Object action) {
          if (!progress.isCanceled()) {
            progress.cancel();
          }
        }
      }, parentDisposable);
    return progress;
  }

  /**
   * @deprecated see {@link ReadTask}
   */
  @Deprecated
  public static void scheduleWithWriteActionPriority(@NotNull ReadTask task) {
    scheduleWithWriteActionPriority(new ProgressIndicatorBase(false, false), task);
  }

  /**
   * @deprecated see {@link ReadTask}
   */
  @Deprecated
  public static @NotNull CompletableFuture<?> scheduleWithWriteActionPriority(@NotNull ProgressIndicator progressIndicator, @NotNull ReadTask readTask) {
    return scheduleWithWriteActionPriority(progressIndicator, AppExecutorUtil.getAppExecutorService(), readTask);
  }

  /**
   * Same as {@link #runInReadActionWithWriteActionPriority(Runnable)}, optionally allowing to pass a {@link ProgressIndicator}
   * instance, which can be used to cancel action externally.
   * @return true if action executed successfully, false if it was canceled by write action before or during execution
   * @deprecated use {@link ReadAction#computeCancellable}
   */
  @Deprecated
  public static boolean runInReadActionWithWriteActionPriority(@NotNull Runnable action, @Nullable ProgressIndicator progressIndicator) {
    AtomicBoolean readActionAcquired = new AtomicBoolean();
    boolean executed = runWithWriteActionPriority(() -> readActionAcquired.set(ApplicationManagerEx.getApplicationEx().tryRunReadAction(action)),
                                           progressIndicator == null ? new ProgressIndicatorBase(false, false) : progressIndicator);
    return readActionAcquired.get() && executed;
  }

  /**
   * This method attempts to run provided action synchronously in a read action, so that, if possible, it wouldn't impact any pending,
   * executing or future write actions (for this to work effectively the action should invoke {@link ProgressManager#checkCanceled()} or
   * {@link ProgressIndicator#checkCanceled()} often enough).
   * It returns {@code true} if action was executed successfully. It returns {@code false} if the action was not
   * executed successfully, i.e. if:
   * <ul>
   * <li>write action was in progress when the method was called</li>
   * <li>write action was pending when the method was called</li>
   * <li>action started to execute, but was aborted using {@link ProcessCanceledException} when some other thread initiated
   * write action</li>
   * </ul>
   * If a caller needs to retry the invocation of this method in a loop, it should consider pausing between attempts, to avoid potential
   * 100% CPU usage. There is also alternative that implements the re-trying logic {@link com.intellij.openapi.application.NonBlockingReadAction}
   * @deprecated use {@link ReadAction#computeCancellable}
   */
  @Deprecated
  public static boolean runInReadActionWithWriteActionPriority(@NotNull Runnable action) {
    return runInReadActionWithWriteActionPriority(action, null);
  }

  /**
   * @return true if action executed successfully, false if it was canceled by write action before or during execution
   * @deprecated It does not make much sense to cancel a BG task,
   * which does not hold a read lock, when a write action is pending.
   * Use {@link CoroutinesKt#readAction} to run a read action,
   * which is cancelled on write. Don't cancel arbitrary tasks on write.
   */
  @Deprecated
  public static boolean runWithWriteActionPriority(@NotNull Runnable action, @NotNull ProgressIndicator progressIndicator) {
    ApplicationEx application = (ApplicationEx)ApplicationManager.getApplication();
    application.assertIsNonDispatchThread();
    Runnable cancellation = indicatorCancellation(progressIndicator);
    if (isWriteActionRunningOrPending(application)) {
      cancellation.run();
      return false;
    }
    return ProgressManager.getInstance().runProcess(() -> {
      try {
        // add listener inside runProcess to avoid cancelling indicator before even starting the progress
        return runActionAndCancelBeforeWrite(application, cancellation, action);
      }
      catch (ProcessCanceledException ignore) {
        return false;
      }
    }, progressIndicator);
  }

  @ApiStatus.Internal
  public static void cancelActionsToBeCancelledBeforeWrite() {
    ProgressIndicatorUtilService.getInstance(ApplicationManager.getApplication()).cancelActionsToBeCancelledBeforeWrite();
  }

  @ApiStatus.Internal
  public static boolean runActionAndCancelBeforeWrite(@NotNull ApplicationEx application,
                                                      @NotNull Runnable cancellation,
                                                      @NotNull Runnable action) {
    return ProgressIndicatorUtilService.getInstance(application).runActionAndCancelBeforeWrite(cancellation, action);
  }

  private static @NotNull Runnable indicatorCancellation(@NotNull ProgressIndicator progressIndicator) {
    return () -> {
      if (!progressIndicator.isCanceled()) {
        progressIndicator.cancel();
      }
    };
  }

  @ApiStatus.Internal
  public static boolean isWriteActionRunningOrPending(@NotNull ApplicationEx application) {
    return application.isWriteActionPending() || application.isWriteActionInProgress();
  }

  /**
   * @deprecated see {@link ReadTask}
   */
  @Deprecated(forRemoval = true)
  public static @NotNull CompletableFuture<?> scheduleWithWriteActionPriority(@NotNull ProgressIndicator progressIndicator,
                                                                              @NotNull Executor executor,
                                                                              @NotNull ReadTask readTask) {
    // invoke later even if on EDT
    // to avoid tasks eagerly restarting immediately, allocating many pooled threads
    // which get cancelled too soon when the next write action arrives in the same EDT batch
    // (can happen when processing multiple VFS events or writing multiple files on save)

    CompletableFuture<?> future = new CompletableFuture<>();
    Application application = ApplicationManager.getApplication();
    application.invokeLater(() -> {
      if (application.isDisposed() || progressIndicator.isCanceled() || future.isCancelled()) {
        future.complete(null);
        return;
      }
      Disposable listenerDisposable = Disposer.newDisposable();
      ApplicationListener listener = new ApplicationListener() {
        @Override
        public void beforeWriteActionStart(@NotNull Object action) {
          if (!progressIndicator.isCanceled()) {
            progressIndicator.cancel();
            readTask.onCanceled(progressIndicator);
          }
        }
      };
      application.addApplicationListener(listener, listenerDisposable);
      future.whenComplete((__, ___) -> Disposer.dispose(listenerDisposable));
      try {
        executor.execute(ClientId.decorateRunnable(new Runnable() {
          @Override
          public void run() {
            ReadTask.Continuation continuation;
            try {
              continuation = runUnderProgress(progressIndicator, readTask);
            }
            catch (Throwable e) {
              future.completeExceptionally(e);
              throw e;
            }
            if (continuation == null) {
              future.complete(null);
            }
            else if (!future.isCancelled()) {
              application.invokeLater(new Runnable() {
                @Override
                public void run() {
                  if (future.isCancelled()) return;

                  Disposer.dispose(listenerDisposable); // remove listener early to prevent firing it during continuation execution
                  try {
                    if (!progressIndicator.isCanceled()) {
                      continuation.getAction().run();
                    }
                  }
                  finally {
                    future.complete(null);
                  }
                }

                @Override
                public String toString() {
                  return "continuation of " + readTask;
                }
              }, continuation.getModalityState());
            }
          }

          @Override
          public String toString() {
            return readTask.toString();
          }
        }));
      }
      catch (Throwable e) {
        future.completeExceptionally(e);
        throw e;
      }
    }, ModalityState.any()); // 'any' to tolerate immediate modality changes (e.g. https://youtrack.jetbrains.com/issue/IDEA-135180)
    return future;
  }

  private static ReadTask.Continuation runUnderProgress(@NotNull ProgressIndicator progressIndicator, @NotNull ReadTask task) {
    return ProgressManager.getInstance().runProcess(() -> {
      try {
        return task.runBackgroundProcess(progressIndicator);
      }
      catch (ProcessCanceledException ignore) {
        return null;
      }
    }, progressIndicator);
  }

  /**
   * Ensure the current EDT activity finishes in case it requires many write actions, with each being delayed a bit
   * by background thread read action (until its first checkCanceled call). Shouldn't be called from under read action.
   *
   * @deprecated It does not make much sense when not dealing with read actions.
   * Non-blocking read actions already do this on their own.
   */
  @Deprecated
  public static void yieldToPendingWriteActions(@Nullable ProgressIndicator indicator) {
    Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed()) {
      throw new IllegalStateException("Mustn't be called from within read action");
    }
    application.assertIsNonDispatchThread();
    Semaphore semaphore = new Semaphore(1);
    application.invokeLater(semaphore::up, ModalityState.any());
    awaitWithCheckCanceled(semaphore, indicator);
  }

  /**
   * @see ProgressIndicatorUtils#yieldToPendingWriteActions(ProgressIndicator)
   * @deprecated It does not make much sense when not dealing with read actions.
   * Non-blocking read actions already do this on their own.
   */
  @Deprecated
  public static void yieldToPendingWriteActions() {
    yieldToPendingWriteActions(ProgressIndicatorProvider.getGlobalProgressIndicator());
  }

  /**
   * Run the given computation with its execution time restricted to the given amount of time in milliseconds.<p></p>
   *
   * Internally, it creates a new {@link ProgressIndicator}, runs the computation with that indicator and cancels it after the timeout.
   * The computation should call {@link ProgressManager#checkCanceled()} frequently enough, so that after the timeout has been exceeded
   * it can stop the execution by throwing {@link ProcessCanceledException}, which will be caught by this {@code withTimeout}.<p></p>
   *
   * If a {@link ProcessCanceledException} happens due to any other reason (e.g. a thread's progress indicator got canceled),
   * it'll be thrown out of this method.
   * @return the computation result or {@code null} if timeout has been exceeded.
   */
  public static @Nullable <T> T withTimeout(long timeoutMs, @NotNull Computable<T> computation) {
    ProgressManager.checkCanceled();
    ProgressIndicator outer = ProgressIndicatorProvider.getGlobalProgressIndicator();
    ProgressIndicator inner = outer != null ? new SensitiveProgressWrapper(outer) : new ProgressIndicatorBase(false, false);
    AtomicBoolean canceledByTimeout = new AtomicBoolean();
    ScheduledFuture<?> cancelProgress = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
      canceledByTimeout.set(true);
      inner.cancel();
    }, timeoutMs, MILLISECONDS);
    try {
      return ProgressManager.getInstance().runProcess(computation, inner);
    }
    catch (ProcessCanceledException e) {
      if (canceledByTimeout.get()) {
        return null;
      }
      throw e; // canceled not by timeout
    }
    finally {
      cancelProgress.cancel(false);
    }
  }

  public static <T, E extends Throwable> T computeWithLockAndCheckingCanceled(@NotNull Lock lock,
                                                                              int timeout,
                                                                              @NotNull TimeUnit timeUnit,
                                                                              @NotNull ThrowableComputable<T, E> computable) throws E, ProcessCanceledException {
    awaitWithCheckCanceled(() -> lock.tryLock(timeout, timeUnit));
    try {
      return computable.compute();
    }
    finally {
      lock.unlock();
    }
  }

  public static void awaitWithCheckCanceled(long millis) {
    long start = System.nanoTime();
    awaitWithCheckCanceled(() -> {
      if (TimeoutUtil.getDurationMillis(start) > millis) return true;
      TimeoutUtil.sleep(ConcurrencyUtil.DEFAULT_TIMEOUT_MS);
      return false;
    });
  }

  public static void awaitWithCheckCanceled(@NotNull Condition condition) {
    awaitWithCheckCanceled(() -> condition.await(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, MILLISECONDS));
  }

  public static void awaitWithCheckCanceled(@NotNull CountDownLatch waiter) {
    awaitWithCheckCanceled(() -> waiter.await(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, MILLISECONDS));
  }

  public static <T> T awaitWithCheckCanceled(@NotNull Future<T> future) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    return awaitWithCheckCanceled(future, indicator);
  }

  public static <T> T awaitWithCheckCanceled(@NotNull Future<T> future, @Nullable ProgressIndicator indicator) {
    int rejectedExecutions = 0;
    while (true) {
      checkCancelledEvenWithPCEDisabled(indicator);
      try {
        return future.get(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, MILLISECONDS);
      }
      catch (TimeoutException ignore) {
      }
      //TODO RC: in a non-cancellable section we could still (re-)throw a (P)CE if the _awaited_ code gets cancelled
      //         (nowadays it is mistakenly considered an error) -- [Daniil et all, private conversation]
      catch (RejectedExecutionException ree) {
        //EA-225412: FJP throws REE (which propagates through futures) e.g. when FJP reaches max
        // threads while compensating for too many managedBlockers -- or when it is shutdown.

        //This branch creates a risk of infinite loop -- i.e. if the current thread itself is somehow
        // responsible for FJP resource exhaustion, hence can't release anything, each consequent
        // future.get() will throw the same REE again and again. So let's limit retries:
        rejectedExecutions++;
        if (rejectedExecutions > MAX_REJECTED_EXECUTIONS_BEFORE_CANCELLATION) {
          //RC: It would be clearer to rethrow ree itself -- but I doubt many callers are ready for it,
          //    while all callers are ready for PCE, hence...
          throw new ProcessCanceledException(ree);
        }
      }
      catch (InterruptedException e) {
        throw new ProcessCanceledException(e);
      }
      catch (Throwable e) {
        Throwable cause = e.getCause();
        if (cause instanceof ProcessCanceledException) {
          throw (ProcessCanceledException)cause;
        }
        if (cause instanceof CancellationException) {
          throw new ProcessCanceledException(cause);
        }
        ExceptionUtil.rethrow(e);
      }
    }
  }

  public static void awaitWithCheckCanceled(@NotNull Lock lock) {
    awaitWithCheckCanceled(() -> lock.tryLock(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, MILLISECONDS));
  }

  public static void awaitWithCheckCanceled(@NotNull ThrowableComputable<Boolean, ? extends Exception> waiter) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    boolean success = false;
    while (!success) {
      checkCancelledEvenWithPCEDisabled(indicator);
      try {
        success = waiter.compute();
      }
      catch (ProcessCanceledException pce) {
        throw pce;
      }
      catch (Exception e) {
        //noinspection InstanceofCatchParameter
        if (!(e instanceof InterruptedException)) {
          LOG.warn(e);
        }
        throw new ProcessCanceledException(e);
      }
    }
  }

  /** Use when a deadlock is possible otherwise. */
  public static void checkCancelledEvenWithPCEDisabled(@Nullable ProgressIndicator indicator) {
    boolean isNonCancelable = Cancellation.isInNonCancelableSection();
    if (isNonCancelable || indicator == null) {
      ((CoreProgressManager)ProgressManager.getInstance()).runCheckCanceledHooks(indicator);
    }
    if (isNonCancelable) return;
    Cancellation.ensureActive();
    if (indicator == null) return;
    indicator.checkCanceled();              // check for cancellation as usual and run the hooks
    if (indicator.isCanceled()) {           // if a just-canceled indicator or PCE is disabled
      indicator.checkCanceled();            // ... let the just-canceled indicator provide a customized PCE
      throw new ProcessCanceledException(); // ... otherwise PCE is disabled so throw it manually
    }
  }

  public static void awaitWithCheckCanceled(@NotNull Semaphore semaphore, @Nullable ProgressIndicator indicator) {
    while (!semaphore.waitFor(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)) {
      checkCancelledEvenWithPCEDisabled(indicator);
    }
  }
}
