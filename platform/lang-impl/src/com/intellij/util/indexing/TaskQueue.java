/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
* Created by Maxim.Mossienko on 7/18/13.
*/
class TaskQueue {
  private final AtomicInteger myDoWorkRequest = new AtomicInteger();
  private final AtomicInteger myUpdatesCount = new AtomicInteger();
  private final BlockingQueue<Runnable> myPendingWriteRequestsQueue = new LinkedBlockingQueue<>();
  private final BlockingQueue<Runnable> myTimestampUpdates = new LinkedBlockingQueue<>();
  private final int myLimit;
  private final int myStealLimit;
  private final int myTimeStampUpdateSizeLimit;

  public TaskQueue(int limit) {
    myLimit = limit;
    myStealLimit = Math.max(1, (int)(limit * 0.01));
    myTimeStampUpdateSizeLimit = 32;
  }

  void submit(@NotNull final Computable<Boolean> update, @NotNull final Runnable successRunnable) {
    int currentTasksCount = myUpdatesCount.incrementAndGet();

    myPendingWriteRequestsQueue.add(() -> {
      try {
        Boolean result = update.compute();
        if (result == Boolean.TRUE) {
          myTimestampUpdates.add(successRunnable);
        }
      }
      finally {
        myUpdatesCount.decrementAndGet();
      }
    });

    if (currentTasksCount > myLimit) {
      Runnable runnable = myPendingWriteRequestsQueue.poll();
      int processed = 0;
      while (runnable != null) {
        runnable.run();
        if (++processed == myStealLimit) break;
        runnable = myPendingWriteRequestsQueue.poll();
      }
    }

    int size = myTimestampUpdates.size();
    if (size > myTimeStampUpdateSizeLimit) {
      applyTimeStamps(size);
    }
  }

  private void applyTimeStamps(final int max) {
    final Runnable runnable = myTimestampUpdates.poll();
    if (runnable == null) return;
    ApplicationManager.getApplication().runReadAction(() -> {
      int updates = 0;
      for (Runnable r = runnable; r != null; r = myTimestampUpdates.poll()) {
        r.run();
        if (++updates == max) break;
      }
    });
  }

  public void ensureUpToDate() {
    try {
      if (Registry.is("idea.concurrent.scanning.files.to.index")) return;
      while(myUpdatesCount.get() > 0) {
        Runnable runnable = myPendingWriteRequestsQueue.poll(10, TimeUnit.MILLISECONDS);
        if (runnable != null) runnable.run();
      }
      applyTimeStamps(Integer.MAX_VALUE);
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void signalUpdateEnd() {
    myDoWorkRequest.decrementAndGet();
  }

  public void signalUpdateStart() {
    int workRequests = myDoWorkRequest.getAndIncrement();

    if (workRequests == 0) {
      if(Registry.is("idea.concurrent.scanning.files.to.index")) return;
      myDoWorkRequest.incrementAndGet();
      // we have 3 content independent indices but only one of them is heavy IO bound so there is no need in more than one thread
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          while(true) {
            Runnable runnable = myPendingWriteRequestsQueue.poll(2000, TimeUnit.MILLISECONDS);
            if (runnable != null) {
              runnable.run();
            } else {
              // we have no work for 2s and there is no currently running updates
              if(myDoWorkRequest.compareAndSet(1, 0)) {
                break;
              }
            }
          }
        }
        catch (InterruptedException ignore) {}
      });
    }
  }
}
