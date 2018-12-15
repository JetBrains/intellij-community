// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class ExternalJavacRunResult implements Future<Boolean> {
  public static final ExternalJavacRunResult FAILURE = new ExternalJavacRunResult() {
    @Override
    public boolean isDone() {
      return true;
    }

    @NotNull
    @Override
    public Boolean get() {
      return Boolean.FALSE;
    }

    @NotNull
    @Override
    public Boolean get(long timeout, @NotNull TimeUnit unit){
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
  @NotNull
  public abstract Boolean get();

  @Override
  @NotNull
  public abstract Boolean get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, TimeoutException;
}
