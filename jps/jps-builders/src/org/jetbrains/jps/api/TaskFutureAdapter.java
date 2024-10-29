// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

@ApiStatus.Internal
public final class TaskFutureAdapter<T> implements TaskFuture<T> {
  private final @NotNull Future<? extends T> myFuture;

  public TaskFutureAdapter(@NotNull Future<? extends T> future) {
    myFuture = future;
  }

  @Override
  public void waitFor() {
    try {
      get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    catch (CancellationException ignored) {
    }
  }

  @Override
  public boolean waitFor(long timeout, TimeUnit unit) {
    try {
      get(timeout, unit);
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    catch (TimeoutException | CancellationException ignored) {
    }
    return isDone();
  }

  // delegates
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
  public T get() throws InterruptedException, ExecutionException {
    return myFuture.get();
  }

  @Override
  public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return myFuture.get(timeout, unit);
  }
}
