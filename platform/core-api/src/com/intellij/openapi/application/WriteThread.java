// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@ApiStatus.Experimental
@ApiStatus.Internal
final class WriteThread {
  private WriteThread() {
  }

  /**
   * Schedules {@code runnable} to execute from under IW lock on some thread later.
   *
   * @param runnable the action to run
   * @return a future representing the result of the scheduled computation
   */
  @NotNull
  public static Future<Void> submit(@NotNull Runnable runnable) {
    return submit(() -> {
      runnable.run();
      return null;
    });
  }

  /**
   * Schedules {@code computable} to execute from under IW lock on some thread later.
   *
   * @param computable the action to run
   * @param <T> return type of scheduled computation
   * @return a future representing the result of the scheduled computation
   */
  @NotNull
  public static <T> Future<T> submit(@NotNull ThrowableComputable<? extends T, ?> computable) {
    CompletableFuture<T> future = new CompletableFuture<>();
    ApplicationManager.getApplication().invokeLaterOnWriteThread(() -> {
      try {
        future.complete(computable.compute());
      }
      catch (Throwable t) {
        future.completeExceptionally(t);
      }
    });
    return future;
  }

  /**
   * Schedules {@code runnable} to execute from under IW lock on some thread later and blocks until
   * the execution is finished.
   *
   * @param runnable the action to run
   */
  public static void invokeAndWait(@NotNull Runnable runnable) {
    try {
      submit(runnable).get();
    }
    catch (InterruptedException ignore) {
    }
    catch (ExecutionException e) {
      ExceptionUtil.rethrowUnchecked(e.getCause());
    }
  }
}
