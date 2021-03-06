// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Author: dmitrylomov
 */
public final class AsyncUtil {
  private static final AsyncFuture<Boolean> TRUE = createConst(true);
  private static final AsyncFuture<Boolean> FALSE = createConst(false);

  public static <V> V get(@NotNull Future<V> result) {
    try {
      return result.get();
    }
    catch (InterruptedException e) {
      throw new Error(e);
    }
    catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException)cause;
      }
      else {
        throw new Error(cause);
      }
    }
  }

  private static AsyncFuture<Boolean> createConst(final boolean result) {
    return new AsyncFuture<Boolean>() {
      @Override
      public void addConsumer(@NotNull Executor executor, @NotNull ResultConsumer<? super Boolean> consumer) {
        consumer.onSuccess(result);
      }

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public boolean isDone() {
        return true;
      }

      @Override
      public Boolean get() {
        return result;
      }

      @Override
      public Boolean get(long timeout, @NotNull TimeUnit unit) {
        return result;
      }
    };
  }

  @NotNull
  public static AsyncFuture<Boolean> wrapBoolean(boolean result) {
    return result ? TRUE : FALSE;
  }
}
