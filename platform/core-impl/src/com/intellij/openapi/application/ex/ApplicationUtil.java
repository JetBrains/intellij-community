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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.concurrent.*;

public class ApplicationUtil {
  // throws exception if can't grab read action right now
  public static <T> T tryRunReadAction(@NotNull final Computable<T> computable) throws CannotRunReadActionException {
    final Ref<T> result = new Ref<T>();
    if (((ApplicationEx)ApplicationManager.getApplication()).tryRunReadAction(new Runnable() {
      @Override
      public void run() {
        result.set(computable.compute());
      }
    })) {
      return result.get();
    }
    throw new CannotRunReadActionException();
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
  public static <T> T runWithCheckCanceled(@NotNull final Computable<T> computable, @NotNull ProgressIndicator indicator) {
    try {
      return runWithCheckCanceled(new Callable<T>() {
        @Override
        public T call() throws Exception {
          return computable.compute();
        }
      }, indicator);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Allows to interrupt a process which does not performs checkCancelled() calls by itself.
   * Note that the process may continue to run in background indefinitely - so <b>avoid using this method unless absolutely needed</b>.
   */
  public static <T> T runWithCheckCanceled(@NotNull final Callable<T> callable, @NotNull final ProgressIndicator indicator) throws Exception {
    final Ref<T> result = Ref.create();
    final Ref<Throwable> error = Ref.create();

    Future<?> future = PooledThreadExecutor.INSTANCE.submit(new Runnable() {
      @Override
      public void run() {
        ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
          @Override
          public void run() {
            try {
              result.set(callable.call());
            }
            catch (Throwable t) {
              error.set(t);
            }
          }
        }, indicator);
      }
    });

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

  public static class CannotRunReadActionException extends RuntimeException {
    @SuppressWarnings({"NullableProblems", "NonSynchronizedMethodOverridesSynchronizedMethod"})
    @Override
    public Throwable fillInStackTrace() {
      return this;
    }
  }
}