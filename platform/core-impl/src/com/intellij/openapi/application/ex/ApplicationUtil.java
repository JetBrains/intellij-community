/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;

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
  public static <T> T runWithCheckCanceled(@NotNull Callable<T> callable) throws ExecutionException, InterruptedException {
    Future<T> future = ApplicationManager.getApplication().executeOnPooledThread(callable);

    while (true) {
      try {
        ProgressManager.checkCanceled();
      }
      catch (ProcessCanceledException e) {
        future.cancel(true);
        throw e;
      }

      try {
        return future.get(200, TimeUnit.MILLISECONDS);
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
