/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.api;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eugene Zhuravlev
 */
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
  public T get() throws InterruptedException, ExecutionException {
    waitFor();
    return null;
  }

  @Override
  public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    if (!waitFor(timeout, unit)) {
      throw new TimeoutException();
    }
    return null;
  }
}
