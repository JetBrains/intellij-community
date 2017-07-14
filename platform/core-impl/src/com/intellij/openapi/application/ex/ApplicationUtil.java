/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ApplicationUtil {
  // throws exception if can't grab read action right now
  public static <T> T tryRunReadAction(@NotNull final Computable<T> computable) throws CannotRunReadActionException {
    final Ref<T> result = new Ref<>();
    tryRunReadAction(() -> result.set(computable.compute()));
    return result.get();
  }

  public static void tryRunReadAction(@NotNull final Runnable computable) throws CannotRunReadActionException {
    if (!((ApplicationEx)ApplicationManager.getApplication()).tryRunReadAction(computable)) {
      throw new CannotRunReadActionException();
    }
  }

  /**
   * Allows to interrupt a process which does not performs checkCancelled() calls by itself.
   * Note that the process may continue to run in background indefinitely - so <b>avoid using this method unless absolutely needed</b>.
   */
  public static <T> T runWithCheckCanceled(@NotNull final Callable<T> callable, @NotNull final ProgressIndicator indicator) throws Exception {
    final Ref<T> result = Ref.create();
    final Ref<Throwable> error = Ref.create();

    Future<?> future = PooledThreadExecutor.INSTANCE.submit(() -> ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      try {
        result.set(callable.call());
      }
      catch (Throwable t) {
        error.set(t);
      }
    }, indicator));

    while (true) {
      try {
        indicator.checkCanceled();
      }
      catch (ProcessCanceledException e) {
        future.cancel(true);
        throw e;
      }

      try {
        future.get(200, TimeUnit.MILLISECONDS);
        ExceptionUtil.rethrowAll(error.get());
        return result.get();
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
    public CannotRunReadActionException() {
    }

    // NB. When &@$ing ForkJoinTask joins task which was exceptionally completed from the other thread
    // it tries to re-create that exception (by reflection) and sets its cause to the original exception.
    // That horrible hack causes all sorts of confusion when we try to analyze the exception cause, e.g. in GlobalInspectionContextImpl.inspectFile().
    // To prevent creation of unneeded wrapped exception we supply this method as a bait which stupid ForkJoinTask calls and immediately poisons itself,
    // causing the original exception to be used unwrapped. (see ForkJoinTask.getThrowableException())
    @SuppressWarnings("unused")
    public CannotRunReadActionException(@Nullable Throwable cause) {
      throw this;
    }
  }
}