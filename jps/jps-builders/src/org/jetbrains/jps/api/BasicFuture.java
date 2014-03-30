/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eugene Zhuravlev
 *         Date: 5/3/12
 */
public class BasicFuture<T> implements Future<T> {
  protected final Semaphore mySemaphore = new Semaphore(1);
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

  public boolean isCancelled() {
    return myCanceledState.get();
  }

  public boolean isDone() {
    return myDone.get();
  }

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

  public T get() throws InterruptedException, ExecutionException {
    waitFor();
    return null;
  }

  public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    if (!waitFor(timeout, unit)) {
      throw new TimeoutException();
    }
    return null;
  }
}
