/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Alarm implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.Alarm");

  private volatile boolean myDisposed;

  private final List<Request> myRequests = new SmartList<Request>();
  private final List<Request> myPendingRequests = new SmartList<Request>();

  private final ExecutorService myExecutorService;

  private static final ThreadPoolExecutor ourSharedExecutorService = ConcurrencyUtil.newSingleThreadExecutor("Alarm pool(shared)", Thread.NORM_PRIORITY - 2);

  private final Object LOCK = new Object();
  protected final ThreadToUse myThreadToUse;

  private JComponent myActivationComponent;

  @Override
  public void dispose() {
    myDisposed = true;
    cancelAllRequests();

    if (myThreadToUse == ThreadToUse.POOLED_THREAD) {
      myExecutorService.shutdown();
    }
    else if (myThreadToUse == ThreadToUse.OWN_THREAD) {
      myExecutorService.shutdown();
      ((ThreadPoolExecutor)myExecutorService).getQueue().clear();
    }
  }

  public enum ThreadToUse {
    SWING_THREAD,
    SHARED_THREAD,
    POOLED_THREAD,
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
    LOG.assertTrue(threadToUse != ThreadToUse.POOLED_THREAD && threadToUse != ThreadToUse.OWN_THREAD,
                   "You must provide parent Disposable for ThreadToUse.POOLED_THREAD and ThreadToUse.OWN_THREAD Alarm");
  }

  public Alarm(@NotNull ThreadToUse threadToUse, Disposable parentDisposable) {
    myThreadToUse = threadToUse;

    if (threadToUse == ThreadToUse.POOLED_THREAD) {
      myExecutorService = new MyExecutor();
    }
    else if(threadToUse == ThreadToUse.OWN_THREAD) {
      myExecutorService = ConcurrencyUtil.newSingleThreadExecutor(
        "Alarm pool(own)", Thread.NORM_PRIORITY - 2);
    }
    else {
      myExecutorService = ourSharedExecutorService;
    }

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
  }

  public void addRequest(@NotNull final Runnable request, final int delay, boolean runWithActiveFrameOnly) {
    if (runWithActiveFrameOnly && !ApplicationManager.getApplication().isActive()) {
      final MessageBus bus = ApplicationManager.getApplication().getMessageBus();
      final MessageBusConnection connection = bus.connect(this);
      connection.subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
        @Override
        public void applicationActivated(IdeFrame ideFrame) {
          connection.disconnect();
          addRequest(request, delay);
        }

        @Override
        public void applicationDeactivated(IdeFrame ideFrame) {
        }
      });
    } else {
      addRequest(request, delay);
    }
  }

  public void addRequest(@NotNull Runnable request, long delayMillis) {
    _addRequest(request, delayMillis, myThreadToUse == ThreadToUse.SWING_THREAD ? ModalityState.current() : null);
  }

  public void addRequest(@NotNull Runnable request, int delayMillis) {
    _addRequest(request, delayMillis, myThreadToUse == ThreadToUse.SWING_THREAD ? ModalityState.current() : null);
  }

  public void addComponentRequest(@NotNull Runnable request, int delay) {
    assert myActivationComponent != null;
    _addRequest(request, delay, ModalityState.stateForComponent(myActivationComponent));
  }

  public void addComponentRequest(@NotNull Runnable request, long delayMillis) {
    assert myActivationComponent != null;
    _addRequest(request, delayMillis, ModalityState.stateForComponent(myActivationComponent));
  }

  public void addRequest(@NotNull Runnable request, int delayMillis, @Nullable final ModalityState modalityState) {
    LOG.assertTrue(myThreadToUse == ThreadToUse.SWING_THREAD);
    _addRequest(request, delayMillis, modalityState);
  }

  public void addRequest(@NotNull Runnable request, long delayMillis, @Nullable final ModalityState modalityState) {
    LOG.assertTrue(myThreadToUse == ThreadToUse.SWING_THREAD);
    _addRequest(request, delayMillis, modalityState);
  }

  protected void _addRequest(@NotNull Runnable request, long delayMillis, ModalityState modalityState) {
    synchronized (LOCK) {
      LOG.assertTrue(!myDisposed, "Already disposed");
      final Request requestToSchedule = new Request(request, modalityState, delayMillis);

      if (myActivationComponent == null || myActivationComponent.isShowing()) {
        _add(requestToSchedule);
      }
      else {
        if (!myPendingRequests.contains(requestToSchedule)) {
          myPendingRequests.add(requestToSchedule);
        }
      }
    }
  }

  private void _add(@NotNull Request requestToSchedule) {
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

  public boolean cancelRequest(@NotNull Runnable request) {
    synchronized (LOCK) {
      cancelRequest(request, myRequests);
      cancelRequest(request, myPendingRequests);
      return true;
    }
  }

  private void cancelRequest(@NotNull Runnable request, @NotNull List<Request> list) {
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

  private int cancelAllRequests(@NotNull List<Request> list) {
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

  public boolean isEmpty() {
    synchronized (LOCK) {
      return myRequests.isEmpty();
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
    private Runnable myTask; // guarded by LOCK
    private final ModalityState myModalityState;
    private Future<?> myFuture; // guarded by LOCK
    private final long myDelay;

    private Request(@NotNull final Runnable task, @Nullable ModalityState modalityState, long delayMillis) {
      synchronized (LOCK) {
        myTask = task;
        myModalityState = modalityState;
        myDelay = delayMillis;
      }
    }

    @Override
    public void run() {
      try {
        if (myDisposed) {
          return;
        }
        synchronized (LOCK) {
          if (myTask == null) return;
        }

        final Runnable scheduledTask = new Runnable() {
          @Override
          public void run() {
            final Runnable task;
            synchronized (LOCK) {
              task = myTask;
              if (task == null) return;
              myTask = null;

              myRequests.remove(Request.this);
              myFuture = null;
            }

            if (myThreadToUse == ThreadToUse.SWING_THREAD && !isEdt()) {
              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  QueueProcessor.runSafely(task);
                }
              });
            }
            else {
              QueueProcessor.runSafely(task);
            }
          }
        };

        if (myModalityState == null) {
          Future<?> future = myExecutorService.submit(scheduledTask);
          synchronized (LOCK) {
            myFuture = future;
          }
        }
        else {
          final Application app = ApplicationManager.getApplication();
          if (app == null) {
            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(scheduledTask);
          }
          else {
            app.invokeLater(scheduledTask, myModalityState);
          }
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    private Runnable getTask() {
      synchronized (LOCK) {
        return myTask;
      }
    }

    public void setFuture(@NotNull ScheduledFuture<?> future) {
      synchronized (LOCK) {
        myFuture = future;
      }
    }

    public ModalityState getModalityState() {
      return myModalityState;
    }

    private void cancel() {
      synchronized (LOCK) {
        if (myFuture != null) {
          myFuture.cancel(false);
          // TODO Use java.util.concurrent.ScheduledThreadPoolExecutor.setRemoveOnCancelPolicy(true) when on jdk 1.7
          ((ScheduledThreadPoolExecutor)JobScheduler.getScheduler()).remove((Runnable)myFuture);
          myFuture = null;
        }
        myTask = null;
      }
    }

    @Override
    public String toString() {
      Runnable task = getTask();
      return super.toString() + (task != null ? task.toString():null);
    }
  }

  public Alarm setActivationComponent(@NotNull final JComponent component) {
    myActivationComponent = component;
    new UiNotifyConnector(component, new Activatable() {
      @Override
      public void showNotify() {
        flushPending();
      }

      @Override
      public void hideNotify() {
      }
    });


    return this;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  private class MyExecutor extends AbstractExecutorService {
    private final AtomicBoolean isShuttingDown = new AtomicBoolean();
    private final QueueProcessor<Runnable> myProcessor = QueueProcessor.createRunnableQueueProcessor();

    @Override
    public void shutdown() {
      myProcessor.clear();
      isShuttingDown.set(myDisposed);
    }

    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown() {
      return isShuttingDown.get();
    }

    @Override
    public boolean isTerminated() {
      return isShutdown() && myProcessor.isEmpty();
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void execute(@NotNull Runnable command) {
      myProcessor.add(command);
    }
  }
}
