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
        mySemaphore.tryAcquire(100L, TimeUnit.MILLISECONDS);
      }
    }
    catch (InterruptedException ignored) {
    }
  }

  public boolean waitFor(long timeout, TimeUnit unit) {
    try {
      if (!isDone()) {
        mySemaphore.tryAcquire(timeout, unit);
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
