// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ApiStatus.Internal
public abstract class ExternalJavacRunResult implements Future<Boolean> {
  public static final ExternalJavacRunResult FAILURE = new ExternalJavacRunResult() {
    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public @NotNull Boolean get() {
      return Boolean.FALSE;
    }

    @Override
    public @NotNull Boolean get(long timeout, @NotNull TimeUnit unit){
      return Boolean.FALSE;
    }
  };

  @Override
  public final boolean cancel(boolean mayInterruptIfRunning) {
    return false; // not supported
  }

  @Override
  public final boolean isCancelled() {
    return false; // not supported, as cancel is handled via CancelStatus
  }

  @Override
  public abstract @NotNull Boolean get();

  @Override
  public abstract @NotNull Boolean get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, TimeoutException;
}
