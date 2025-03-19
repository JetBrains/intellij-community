// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@ApiStatus.Internal
public class BasicFuture<T> implements TaskFuture<T> {
  private final Semaphore mySemaphore = new Semaphore(1);
  private final AtomicBoolean myDone = new AtomicBoolean(false);
  private final AtomicBoolean myCanceledState = new AtomicBoolean(false);

  public BasicFuture() {
    mySemaphore.acquireUninterruptibly();
  }

  public void setDone() {
    if (!myDone.getAndSet(true)) {
      mySemaphore.release();
    }
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    if (isDone()) {
      return false;
    }
    if (!myCanceledState.getAndSet(true)) {
      try {
        performCancel();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return true;
  }

  protected void performCancel() throws Exception {
  }

  @Override
  public boolean isCancelled() {
    return myCanceledState.get();
  }

  @Override
  public boolean isDone() {
    return myDone.get();
  }

  @Override
  public void waitFor() {
    try {
      while (!isDone()) {
        if (mySemaphore.tryAcquire(100L, TimeUnit.MILLISECONDS)) {
          mySemaphore.release();
        }
      }
    }
    catch (InterruptedException ignored) {
    }
  }

  @Override
  public boolean waitFor(long timeout, TimeUnit unit) {
    try {
      if (!isDone()) {
        if (mySemaphore.tryAcquire(timeout, unit)) {
          mySemaphore.release();
        }
      }
    }
    catch (InterruptedException ignored) {
    }
    return isDone();
  }

  @Override
  public T get() {
    waitFor();
    return null;
  }

  @Override
  public T get(long timeout, @NotNull TimeUnit unit) throws TimeoutException {
    if (!waitFor(timeout, unit)) {
      throw new TimeoutException();
    }
    return null;
  }
}
