// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

public class ApplicationUtil {
  // throws exception if can't grab read action right now
  public static <T> T tryRunReadAction(@NotNull final Computable<T> computable) throws CannotRunReadActionException {
    final Ref<T> result = new Ref<>();
    tryRunReadAction(() -> result.set(computable.compute()));
    return result.get();
  }

  public static void tryRunReadAction(@NotNull final Runnable computable) throws CannotRunReadActionException {
    if (!((ApplicationEx)ApplicationManager.getApplication()).tryRunReadAction(computable)) {
      throw CannotRunReadActionException.create();
    }
  }

  /**
   * Allows to interrupt a process which does not performs checkCancelled() calls by itself.
   * Note that the process may continue to run in background indefinitely - so <b>avoid using this method unless absolutely needed</b>.
   */
  public static <T> T runWithCheckCanceled(@NotNull final Callable<T> callable, @NotNull final ProgressIndicator indicator) throws Exception {
    final Ref<T> result = Ref.create();
    final Ref<Throwable> error = Ref.create();

    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      try {
        result.set(callable.call());
      }
      catch (Throwable t) {
        error.set(t);
      }
    }, indicator));

    try {
      runWithCheckCanceled(future, indicator);
      ExceptionUtil.rethrowAll(error.get());
    }
    catch (ProcessCanceledException e) {
      future.cancel(true);
      throw e;
    }
    return result.get();
  }

  /**
   * Waits for {@code future} to be complete, or the current thread's indicator to be canceled.
   * Note that {@code future} will not be cancelled by this method.
   */
  public static <T> T runWithCheckCanceled(@NotNull Future<T> future,
                                           @NotNull final ProgressIndicator indicator) throws ExecutionException {
    while (true) {
      indicator.checkCanceled();

      try {
        return future.get(25, TimeUnit.MILLISECONDS);
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

  public static class CannotRunReadActionException extends ProcessCanceledException {
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