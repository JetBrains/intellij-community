// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.EdtReplacementThread;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.EdtInvocationManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class ApplicationUtil {
  // throws exception if can't grab read action right now
  public static <T> T tryRunReadAction(final @NotNull Computable<T> computable) throws CannotRunReadActionException {
    final Ref<T> result = new Ref<>();
    if (!((ApplicationEx)ApplicationManager.getApplication()).tryRunReadAction(() -> result.set(computable.compute()))) {
      throw CannotRunReadActionException.create();
    }
    return result.get();
  }

  /**
   * Allows interrupting a process which does not perform checkCancelled() calls by itself.
   * Note that the process may continue to run in background indefinitely - so <b>avoid using this method unless absolutely needed</b>.
   */
  public static <T> T runWithCheckCanceled(final @NotNull Callable<? extends T> callable, final @NotNull ProgressIndicator indicator) throws Exception {
    final Ref<T> result = new Ref<>();
    final Ref<Throwable> error = new Ref<>();

    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        try {
          result.set(callable.call());
        }
        catch (Throwable t) {
          error.set(t);
        }
      }, indicator);
    });

    try {
      runWithCheckCanceled(future, indicator);
      ExceptionUtil.rethrowAll(error.get());
    }
    catch (ProcessCanceledException e) {
      future.cancel(false);
      throw e;
    }
    return result.get();
  }

  /**
   * Waits for {@code future} to be complete, or the current thread's indicator to be canceled.
   * Note that {@code future} will not be cancelled by this method.<br/>
   * See also {@link com.intellij.openapi.progress.util.ProgressIndicatorUtils#awaitWithCheckCanceled(Future)} which throws no checked exceptions.
   */
  public static <T> T runWithCheckCanceled(@NotNull Future<T> future,
                                           final @NotNull ProgressIndicator indicator) throws ExecutionException {
    while (true) {
      indicator.checkCanceled();

      try {
        return future.get(10, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException e) {
        throw new ProcessCanceledException(e);
      }
      catch (TimeoutException ignored) { }
    }
  }

  public static void showDialogAfterWriteAction(@NotNull Runnable runnable) {
    Application application = ApplicationManager.getApplication();
    if (application.isWriteAccessAllowed()) {
      application.invokeLater(runnable);
    }
    else {
      runnable.run();
    }
  }

  public static void invokeLaterSomewhere(@NotNull EdtReplacementThread thread, @NotNull ModalityState modalityState, @NotNull Runnable r) {
    switch (thread) {
      case EDT:
        SwingUtilities.invokeLater(r);
        break;
      case WT:
        ApplicationManager.getApplication().invokeLaterOnWriteThread(r, modalityState);
        break;
      case EDT_WITH_IW:
        ApplicationManager.getApplication().invokeLater(r, modalityState);
        break;
    }
  }

  public static void invokeAndWaitSomewhere(@NotNull EdtReplacementThread thread, @NotNull ModalityState modalityState, @NotNull Runnable r) {
    switch (thread) {
      case EDT:
        if (!SwingUtilities.isEventDispatchThread() && ApplicationManager.getApplication().isWriteIntentLockAcquired()) {
          Logger.getInstance(ApplicationUtil.class).error("Can't invokeAndWait from WT to EDT: probably leads to deadlock");
        }
        EdtInvocationManager.invokeAndWaitIfNeeded(r);
        break;
      case WT:
        if (ApplicationManager.getApplication().isWriteIntentLockAcquired()) {
          r.run();
        }
        else if (SwingUtilities.isEventDispatchThread()) {
          Logger.getInstance(ApplicationUtil.class).error("Can't invokeAndWait from EDT to WT");
        }
        else {
          Semaphore s = new Semaphore(1);
          AtomicReference<Throwable> throwable = new AtomicReference<>();
          ApplicationManager.getApplication().invokeLaterOnWriteThread(() -> {
            try {
              r.run();
            }
            catch (Throwable t) {
              throwable.set(t);
            }
            finally {
              s.up();
            }
          }, modalityState);
          s.waitFor();

          if (throwable.get() != null) {
            ExceptionUtil.rethrow(throwable.get());
          }
        }
        break;
      case EDT_WITH_IW:
        if (!SwingUtilities.isEventDispatchThread() && ApplicationManager.getApplication().isWriteIntentLockAcquired()) {
          Logger.getInstance(ApplicationUtil.class).error("Can't invokeAndWait from WT to EDT: probably leads to deadlock");
        }
        ApplicationManager.getApplication().invokeAndWait(r, modalityState);
        break;
    }
  }

  public static final class CannotRunReadActionException extends ProcessCanceledException {
    // When ForkJoinTask joins task which was exceptionally completed from the other thread
    // it tries to re-create that exception (by reflection) and sets its cause to the original exception.
    // That horrible hack causes all sorts of confusion when we try to analyze the exception cause, e.g. in GlobalInspectionContextImpl.inspectFile().
    // To prevent creation of unneeded wrapped exception we restrict constructor visibility to private so that stupid ForkJoinTask has no choice
    // but to use the original exception. (see ForkJoinTask.getThrowableException())
    public static CannotRunReadActionException create() {
      return new CannotRunReadActionException();
    }
    private CannotRunReadActionException() {
    }

  }
}