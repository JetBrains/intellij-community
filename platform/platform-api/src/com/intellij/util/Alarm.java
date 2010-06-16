/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Alarm implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.Alarm");

  private boolean myDisposed;

  private final List<Request> myRequests = new ArrayList<Request>();
  private final List<Request> myPendingRequests = new ArrayList<Request>();

  private static final ThreadPoolExecutor ourSharedExecutorService = ConcurrencyUtil.newSingleThreadExecutor("Alarm pool(shared)", Thread.NORM_PRIORITY - 2);

  private final Object LOCK = new Object();
  private final ThreadToUse myThreadToUse;

  private JComponent myActivationComponent;

  public void dispose() {
    myDisposed = true;
    cancelAllRequests();
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

  public Alarm(@NotNull ThreadToUse threadToUse) {
    this(threadToUse, null);
    LOG.assertTrue(threadToUse != ThreadToUse.OWN_THREAD, "You must provide parent Disposable for ThreadToUse.OWN_THREAD Alarm");
  }
  public Alarm(@NotNull ThreadToUse threadToUse, Disposable parentDisposable) {
    myThreadToUse = threadToUse;

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
  }

  public void addRequest(final Runnable request, int delayMillis) {
    _addRequest(request, delayMillis, myThreadToUse == ThreadToUse.SWING_THREAD ? ModalityState.current() : null);
  }

  public void addComponentRequest(Runnable request, int delay) {
    assert myActivationComponent != null;
    _addRequest(request, delay, ModalityState.stateForComponent(myActivationComponent));
  }

  public void addRequest(final Runnable request, int delayMillis, final ModalityState modalityState) {
    LOG.assertTrue(myThreadToUse == ThreadToUse.SWING_THREAD);
    _addRequest(request, delayMillis, modalityState);
  }

  private void _addRequest(final Runnable request, int delayMillis, ModalityState modalityState) {
    synchronized (LOCK) {
      final Request requestToSchedule = new Request(request, modalityState, delayMillis);

      if (myActivationComponent == null || myActivationComponent.isShowing()) {
        _add(requestToSchedule);
      } else {
        if (!myPendingRequests.contains(requestToSchedule)) {
          myPendingRequests.add(requestToSchedule);
        }
      }
    }
  }

  private void _add(final Request requestToSchedule) {
    final ScheduledFuture<?> future = JobScheduler.getScheduler().schedule(requestToSchedule, requestToSchedule.myDelay, TimeUnit.MILLISECONDS);
    requestToSchedule.setFuture(future);
    myRequests.add(requestToSchedule);
  }

  private void flushPending() {
    for (Request each : myPendingRequests) {
      _add(each);
    }

    myPendingRequests.clear();
  }

  public boolean cancelRequest(Runnable request) {
    synchronized (LOCK) {
      cancelRequest(request, myRequests);
      cancelRequest(request, myPendingRequests);
      return true;
    }
  }

  private void cancelRequest(final Runnable request, final List<Request> list) {
    for (int i = list.size()-1; i>=0; i--) {
      Request r = list.get(i);
      if (r.getTask() == request) {
        r.cancel();
        list.remove(i);
      }
    }
  }

  public int cancelAllRequests() {
    synchronized (LOCK) {
      int count = cancelAllRequests(myRequests);
      cancelAllRequests(myPendingRequests);
      return count;
    }
  }

  private int cancelAllRequests(final List<Request> list) {
    int count = 0;
    for (Request request : list) {
      count++;
      request.cancel();
    }
    list.clear();
    return count;
  }

  public int getActiveRequestCount() {
    synchronized (LOCK) {
      return myRequests.size();
    }
  }

  protected boolean isEdt() {
    return isEventDispatchThread();
  }

  public static boolean isEventDispatchThread() {
    final Application app = ApplicationManager.getApplication();
    return app != null && app.isDispatchThread() || EventQueue.isDispatchThread();
  }

  private class Request implements Runnable {
    private Runnable myTask;
    private final ModalityState myModalityState;
    private Future<?> myFuture;
    private final int myDelay;

    private Request(final Runnable task, final ModalityState modalityState, int delayMillis) {
      myTask = task;
      myModalityState = modalityState;
      myDelay = delayMillis;
    }

    public void run() {
      try {
        if (!myDisposed) {
          synchronized (LOCK) {
            if (myTask == null) return;
          }

          final Runnable scheduledTask = new Runnable() {
            public void run() {
              final Runnable task;
              synchronized (LOCK) {
                task = myTask;
                if (task == null) return;
                myTask = null;

                myRequests.remove(Request.this);
              }

              if (myThreadToUse == ThreadToUse.SWING_THREAD && !isEdt()) {
                try {
                  SwingUtilities.invokeAndWait(task);
                }
                catch (Exception e) {
                  LOG.error(e);
                }
              }
              else {
                try {
                  task.run();
                }
                catch (Exception e) {
                  LOG.error(e);
                }
              }
            }
          };

          if (myModalityState != null) {
            final Application app = ApplicationManager.getApplication();
            if (app != null) {
              app.invokeLater(scheduledTask, myModalityState);
            }
            else {
              SwingUtilities.invokeLater(scheduledTask);
            }
          }
          else {
            myFuture = (myThreadToUse == ThreadToUse.SHARED_THREAD)
                       ? ourSharedExecutorService.submit(scheduledTask)
                       : ApplicationManager.getApplication().executeOnPooledThread(scheduledTask);
          }
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    private Runnable getTask() {
      return myTask;
    }

    public void setFuture(final ScheduledFuture<?> future) {
      myFuture = future;
    }

    public ModalityState getModalityState() {
      return myModalityState;
    }

    private void cancel() {
      synchronized (LOCK) {
        if (myFuture != null) {
          myFuture.cancel(false);
        }
        myTask = null;
      }
    }
  }

  public Alarm setActivationComponent(@NotNull final JComponent component) {
    myActivationComponent = component;
    new UiNotifyConnector(component, new Activatable() {
      public void showNotify() {
        flushPending();
      }

      public void hideNotify() {
      }
    });


    return this;
  }
}
