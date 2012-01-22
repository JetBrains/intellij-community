/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
* @author Eugene Zhuravlev
*         Date: 9/13/11
*/
public class RequestFuture<T> implements Future {
  private final Semaphore mySemaphore = new Semaphore(1);
  private final AtomicBoolean myDone = new AtomicBoolean(false);
  private final T myHandler;
  private final UUID myRequestID;
  @Nullable private final CancelAction<T> myCancelAction;
  private final AtomicBoolean myCanceledState = new AtomicBoolean(false);

  public interface CancelAction<T> {
    void cancel(RequestFuture<T> future) throws Exception;
  }

  public RequestFuture(T handler, UUID requestID, @Nullable CancelAction<T> cancelAction) {
    myHandler = handler;
    myRequestID = requestID;
    myCancelAction = cancelAction;
    mySemaphore.acquireUninterruptibly();
  }

  public void setDone() {
    if (!myDone.getAndSet(true)) {
      mySemaphore.release();
    }
  }

  public UUID getRequestID() {
    return myRequestID;
  }

  public boolean cancel(boolean mayInterruptIfRunning) {
    if (isDone()) {
      return false;
    }
    if (!myCanceledState.getAndSet(true)) {
      try {
        if (myCancelAction != null) {
          myCancelAction.cancel(this);
        }
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return true;
  }

  public boolean isCancelled() {
    return myCanceledState.get();
  }

  public boolean isDone() {
    return myDone.get();
  }

  public Object get() throws InterruptedException, ExecutionException {
    while (!isDone()) {
      mySemaphore.tryAcquire(100L, TimeUnit.MILLISECONDS);
    }
    return null;
  }

  public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    if (!isDone()) {
      mySemaphore.tryAcquire(timeout, unit);
    }
    return null;
  }

  public T getResponseHandler() {
    return myHandler;
  }
}
