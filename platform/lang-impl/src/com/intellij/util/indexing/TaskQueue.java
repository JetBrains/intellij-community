package com.intellij.util.indexing;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Computable;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
* Created by Maxim.Mossienko on 7/18/13.
*/
class TaskQueue {
  private final AtomicInteger myDoWorkRequest = new AtomicInteger();
  private final AtomicInteger myUpdatesCount = new AtomicInteger();
  private final LinkedBlockingQueue<Runnable> myPendingWriteRequestsQueue = new LinkedBlockingQueue<Runnable>();
  private final LinkedBlockingQueue<Runnable> myTimestampUpdates = new LinkedBlockingQueue<Runnable>();
  private final int myLimit;
  private final int myStealLimit;
  private final int myTimeStampUpdateSizeLimit;

  public TaskQueue(int limit) {
    myLimit = limit;
    myStealLimit = Math.max(1, (int)(limit * 0.01));
    myTimeStampUpdateSizeLimit = 32;
  }

  void submit(final Computable<Boolean> update, final Runnable successRunnable) {
    int currentTasksCount = myUpdatesCount.incrementAndGet();

    myPendingWriteRequestsQueue.add(new Runnable() {
      @Override
      public void run() {
        try {
          Boolean result = update.compute();
          if (result == Boolean.TRUE) {
            myTimestampUpdates.add(successRunnable);
          }
        }
        finally {
          myUpdatesCount.decrementAndGet();
        }
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

  private void applyTimeStamps(int max) {
    Runnable runnable = myTimestampUpdates.poll();
    if (runnable == null) return;
    int updates = 0;
    AccessToken accessToken = ReadAction.start();
    try {
      while(runnable != null) {
        runnable.run();
        if (++updates == max) break;
        runnable = myTimestampUpdates.poll();
      }
    } finally {
      accessToken.finish();
    }
  }

  public void ensureUpToDate() {
    try {
      while(myUpdatesCount.get() > 0) {
        Runnable runnable = myPendingWriteRequestsQueue.poll(10, TimeUnit.MILLISECONDS);
        if (runnable != null) runnable.run();
      }
      applyTimeStamps(Integer.MAX_VALUE);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void signalUpdateEnd() {
    myDoWorkRequest.decrementAndGet();
  }

  public void signalUpdateStart() {
    int workRequests = myDoWorkRequest.getAndIncrement();

    if (workRequests == 0) {
      myDoWorkRequest.incrementAndGet();
      // we have 3 content independent indices but only one of them is heavy IO bound so there is no need in more than one thread
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
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
        }
      });
    }
  }
}
