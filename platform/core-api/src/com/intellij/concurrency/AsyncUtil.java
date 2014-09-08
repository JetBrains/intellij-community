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
package com.intellij.concurrency;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Author: dmitrylomov
 */
public class AsyncUtil {
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
      public void addConsumer(@NotNull Executor executor, @NotNull ResultConsumer<Boolean> consumer) {
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
