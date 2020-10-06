// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

import com.google.common.util.concurrent.SettableFuture;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class AsyncFutureResultImpl<V> implements AsyncFutureResult<V> {
  private final SettableFuture<V> myFuture;

  public AsyncFutureResultImpl() {
    myFuture = SettableFuture.create();
  }

  @Override
  public void addConsumer(@NotNull Executor executor, @NotNull final ResultConsumer<? super V> consumer) {
    myFuture.addListener(() -> {
      try {
        final V result = myFuture.get();
        consumer.onSuccess(result);
      }
      catch (ExecutionException e) {
        consumer.onFailure(e.getCause());
      }
      catch (Throwable throwable) {
        consumer.onFailure(throwable);
      }
    }, executor);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return myFuture.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return myFuture.isCancelled();
  }

  @Override
  public boolean isDone() {
    return myFuture.isDone();
  }

  @Override
  public V get() throws InterruptedException, ExecutionException {
    return myFuture.get();
  }

  @Override
  public V get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return myFuture.get(timeout, unit);
  }

  @Override
  public void set(V value) {
    if (!myFuture.set(value)) {
      throw new Error("already set");
    }
  }

  @Override
  public void setException(@NotNull Throwable t) {
    if (!myFuture.setException(t)) {
      throw new Error("already excepted");
    }
  }
}
