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

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
* @author Eugene Zhuravlev
*         Date: 9/13/11
*/
public class RequestFuture implements Future {
  private final Semaphore mySemaphore = new Semaphore(1);
  private final AtomicBoolean myDone = new AtomicBoolean(false);
  private final JpsServerResponseHandler myHandler;
  private final UUID myRequestID;

  public RequestFuture(JpsServerResponseHandler handler, UUID requestID) {
    myHandler = handler;
    myRequestID = requestID;
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
    return false;
  }

  public boolean isCancelled() {
    return false;
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

  public JpsServerResponseHandler getHandler() {
    return myHandler;
  }
}
