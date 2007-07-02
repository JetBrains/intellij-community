/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Alarm implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.Alarm");

  private boolean myDisposed;
  private List<Request> myRequests = new ArrayList<Request>();
  private final ExecutorService myExecutorService;

  @NonNls private static final String THREADS_NAME = "Alarm pool";
  private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
    public Thread newThread(final Runnable r) {
      return new Thread(r, THREADS_NAME);
    }
  };
  private static final ExecutorService ourSharedExecutorService = Executors.newSingleThreadExecutor(THREAD_FACTORY);

  private final Object LOCK = new Object();
  private ThreadToUse myThreadToUse;

  public void dispose() {
    myDisposed = true;
    cancelAllRequests();
    if (myThreadToUse == ThreadToUse.OWN_THREAD) {
      myExecutorService.shutdown();
    }
  }

  public enum ThreadToUse {
    SWING_THREAD,
    SHARED_THREAD,
    OWN_THREAD
  }

  /**
   * Creates alarm that works in Swing thread
   */
  public Alarm() {
    this(ThreadToUse.SWING_THREAD);
  }

  public Alarm(Disposable parentDisposable) {
    this(ThreadToUse.SWING_THREAD, parentDisposable);
  }

  public Alarm(ThreadToUse threadToUse) {
    this(threadToUse, null);
  }
  public Alarm(ThreadToUse threadToUse, Disposable parentDisposable) {
    myThreadToUse = threadToUse;
    myExecutorService = (threadToUse == ThreadToUse.OWN_THREAD) ? Executors.newSingleThreadExecutor(THREAD_FACTORY) : ourSharedExecutorService;

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
  }

  public void addRequest(final Runnable request, int delay) {
    _addRequest(request, delay, myThreadToUse == ThreadToUse.SWING_THREAD ? ModalityState.current() : null);
  }

  public void addRequest(final Runnable request, int delay, ModalityState modalityState) {
    LOG.assertTrue(myThreadToUse == ThreadToUse.SWING_THREAD);
    _addRequest(request, delay, modalityState);
  }

  private void _addRequest(final Runnable request, int delay, ModalityState modalityState) {
    synchronized (LOCK) {
      final Request requestToSchedule = new Request(request, modalityState);
      final ScheduledFuture<?> future = JobScheduler.getScheduler().schedule(requestToSchedule, delay, TimeUnit.MILLISECONDS);
      requestToSchedule.setFuture(future);
      myRequests.add(requestToSchedule);
    }
  }

  public boolean cancelRequest(Runnable request) {
    synchronized (LOCK) {
      for (int i = myRequests.size()-1; i>=0; i--) {
        Request r = myRequests.get(i);
        if (r.getTask() == request) {
          r.getFuture().cancel(false);
          myRequests.remove(i);
        }
      }

      return true;
    }
  }

  public int cancelAllRequests() {
    synchronized (LOCK) {
      int count = 0;
      for (Request request : myRequests) {
        count++;
        request.getFuture().cancel(false);
      }
      myRequests.clear();
      return count;
    }
  }

  public int getActiveRequestCount() {
    synchronized (LOCK) {
      return myRequests.size();
    }
  }

  private class Request implements Runnable {
    private final Runnable myTask;
    private final ModalityState myModalityState;
    private Future<?> myFuture;

    public Request(final Runnable task, final ModalityState modalityState) {
      myTask = task;
      myModalityState = modalityState;
    }

    public void run() {
      try {
        if (!myDisposed) {
          if (myModalityState != null) {
            synchronized (LOCK) {
              myRequests.remove(this);
            }
            ApplicationManager.getApplication().invokeLater(myTask, myModalityState);
          }
          else {
            myFuture = myExecutorService.submit(new Runnable() {
              public void run() {
                synchronized (LOCK) {
                  myRequests.remove(Request.this);
                }

                myTask.run();
              }
            });
          }
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    public Runnable getTask() {
      return myTask;
    }

    public Future<?> getFuture() {
      return myFuture;
    }

    public void setFuture(final ScheduledFuture<?> future) {
      myFuture = future;
    }

    public ModalityState getModalityState() {
      return myModalityState;
    }
  }
}