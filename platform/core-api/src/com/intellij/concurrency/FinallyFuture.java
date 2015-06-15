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
package com.intellij.concurrency;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Author: dmitrylomov
 */
public class FinallyFuture<V> implements AsyncFuture<V> {
  private final DoOnce myFinallyBlock;
  private final AsyncFuture<V> myInner;

  public FinallyFuture(@NotNull AsyncFuture<V> inner, @NotNull Runnable finallyBlock) {
    myInner = inner;
    myFinallyBlock = new DoOnce(finallyBlock);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean cancel = myInner.cancel(mayInterruptIfRunning);
    if (cancel) {
      myFinallyBlock.execute();
    }
    return cancel;
  }

  @Override
  public boolean isCancelled() {
    return myInner.isDone();
  }

  @Override
  public boolean isDone() {
    return myInner.isDone();
  }

  @Override
  public V get() throws InterruptedException, ExecutionException {
    try {
      return myInner.get();
    }
    finally {
      myFinallyBlock.execute();
    }
  }

  @Override
  public void addConsumer(@NotNull Executor executor, @NotNull final ResultConsumer<V> consumer) {
    myInner.addConsumer(executor, new ResultConsumer<V>() {
      @Override
      public void onSuccess(V value) {
        try {
          myFinallyBlock.execute();
          consumer.onSuccess(value);
        }
        catch (Throwable t) {
          consumer.onFailure(t);
        }
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        try {
          myFinallyBlock.execute();
        }
        catch (Throwable t1) {
          t = t1;
        }
        consumer.onFailure(t);
      }
    });
  }

  @Override
  public V get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    boolean timeoutOccurred = false;
    try {
      try {
        return myInner.get(timeout, unit);
      }
      catch (TimeoutException t) {
        timeoutOccurred = true;
        throw t;
      }
    }
    finally {
      if (!timeoutOccurred) {
        myFinallyBlock.execute();
      }
    }
  }
}
