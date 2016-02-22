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
package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Allows to schedule Runnable instances (requests) to be executed after a specific time interval on a specific thread.
 * Use {@link #addRequest} methods to schedule the requests.
 * Two requests scheduled with the same delay are executed sequentially, one after the other.
 * {@link #cancelAllRequests()} and {@link #cancelRequest(Runnable)} allow to cancel already scheduled requests.
 */
public class Alarm implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.Alarm");

  private volatile boolean myDisposed;

  private final List<Request> myRequests = new SmartList<Request>(); // guarded by LOCK
  private final List<Request> myPendingRequests = new SmartList<Request>(); // guarded by LOCK

  private final ScheduledExecutorService myExecutorService;

  private final Object LOCK = new Object();
  final ThreadToUse myThreadToUse;

  private JComponent myActivationComponent;

  @Override
  public void dispose() {
    if (!myDisposed) {
      myDisposed = true;
      cancelAllRequests();

      myExecutorService.shutdownNow();
    }
  }

  private void checkDisposed() {
    LOG.assertTrue(!myDisposed, "Already disposed");
  }

  public enum ThreadToUse {
    /**
     * Run request in Swing EventDispatchThread. This is the default.
     * NB: <i>Requests shouldn't take long to avoid UI freezes.</i>
     */
    SWING_THREAD,

    /**
     * @deprecated Use {@link #POOLED_THREAD} instead
     */
    @Deprecated
    SHARED_THREAD,

    /**
     * Run requests in one of application pooled threads.
     *
     * @see Application#executeOnPooledThread(Callable)
     */
    POOLED_THREAD,

    /**
     * @deprecated Use {@link #POOLED_THREAD} instead
     */
    @Deprecated
    OWN_THREAD
  }

  /**
   * Creates alarm that works in Swing thread
   */
  public Alarm() {
    this(ThreadToUse.SWING_THREAD);
  }

  public Alarm(@NotNull Disposable parentDisposable) {
    this(ThreadToUse.SWING_THREAD, parentDisposable);
  }

  public Alarm(@NotNull ThreadToUse threadToUse) {
    this(threadToUse, null);
    LOG.assertTrue(threadToUse != ThreadToUse.POOLED_THREAD && threadToUse != ThreadToUse.OWN_THREAD,
                   "You must provide parent Disposable for ThreadToUse.POOLED_THREAD and ThreadToUse.OWN_THREAD Alarm");
  }

  public Alarm(@NotNull ThreadToUse threadToUse, @Nullable Disposable parentDisposable) {
    myThreadToUse = threadToUse;

    myExecutorService = // have to restrict the number of running tasks because otherwise the (implicit) contract of
                        // "addRequests with the same delay are executed in order" will be broken
                        AppExecutorUtil.createBoundedScheduledExecutorService(1);

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
  }

  public void addRequest(@NotNull final Runnable request, final int delay, boolean runWithActiveFrameOnly) {
    if (runWithActiveFrameOnly && !ApplicationManager.getApplication().isActive()) {
      final MessageBus bus = ApplicationManager.getApplication().getMessageBus();
      final MessageBusConnection connection = bus.connect(this);
      connection.subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener.Adapter() {
        @Override
        public void applicationActivated(IdeFrame ideFrame) {
          connection.disconnect();
          addRequest(request, delay);
        }
      });
    }
    else {
      addRequest(request, delay);
    }
  }

  private ModalityState getModalityState() {
    if (myThreadToUse != ThreadToUse.SWING_THREAD) return null;
    Application application = ApplicationManager.getApplication();
    if (application == null) return null;
    return application.getCurrentModalityState();
  }

  public void addRequest(@NotNull Runnable request, long delayMillis) {
    _addRequest(request, delayMillis, getModalityState());
  }

  public void addRequest(@NotNull Runnable request, int delayMillis) {
    _addRequest(request, delayMillis, getModalityState());
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

  void _addRequest(@NotNull Runnable request, long delayMillis, @Nullable ModalityState modalityState) {
    synchronized (LOCK) {
      checkDisposed();
      final Request requestToSchedule = new Request(request, modalityState, delayMillis);

      if (myActivationComponent == null || myActivationComponent.isShowing()) {
        _add(requestToSchedule);
      }
      else if (!myPendingRequests.contains(requestToSchedule)) {
        myPendingRequests.add(requestToSchedule);
      }
    }
  }

  // must be called under LOCK
  private void _add(@NotNull Request requestToSchedule) {
    requestToSchedule.schedule();
    myRequests.add(requestToSchedule);
  }

  private void flushPending() {
    synchronized (LOCK) {
      for (Request each : myPendingRequests) {
        _add(each);
      }

      myPendingRequests.clear();
    }
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
      if (r.myTask == request) {
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

  @TestOnly
  public void flush() {
    List<Pair<Request, Runnable>> requests;
    synchronized (LOCK) {
      if (myRequests.isEmpty()) {
        return;
      }

      requests = new SmartList<Pair<Request, Runnable>>();
      for (Request request : myRequests) {
        Runnable existingTask = request.cancel();
        if (existingTask != null) {
          requests.add(Pair.create(request, existingTask));
        }
      }
      myRequests.clear();
    }

    for (Pair<Request, Runnable> request : requests) {
      synchronized (LOCK) {
        request.first.myTask = request.second;
      }
      request.first.run();
    }
  }

  @TestOnly
  void waitForAllExecuted(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    List<Request> requests;
    synchronized (LOCK) {
      requests = new ArrayList<Request>(myRequests);
    }

    for (Request request : requests) {
      Future<?> future;
      synchronized (LOCK) {
        future = request.myFuture;
      }
      if (future != null) {
        future.get(timeout, unit);
      }
    }
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
          if (myTask == null) {
            return;
          }
        }

        final Runnable scheduledTask = new Runnable() {
          @Override
          public void run() {
            final Runnable task;
            synchronized (LOCK) {
              task = myTask;
              myTask = null;

              myRequests.remove(Request.this);
              myFuture = null;
            }
            if (task == null) return;

            if (myThreadToUse == ThreadToUse.SWING_THREAD && !isEdt()) {
              //noinspection SSBasedInspection
              EdtInvocationManager.getInstance().invokeLater(new Runnable() {
                @Override
                public void run() {
                  if (!myDisposed) {
                    QueueProcessor.runSafely(task);
                  }
                }
              });
            }
            else {
              QueueProcessor.runSafely(task);
            }
          }

          @Override
          public String toString() {
            return "ScheduledTask "+Request.this;
          }
        };

        if (myModalityState == null) {
          scheduledTask.run();
        }
        else {
          final Application app = ApplicationManager.getApplication();
          if (app == null) {
            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(scheduledTask);
          }
          else if (app.isDispatchThread() && app.getCurrentModalityState().equals(myModalityState)) {
            scheduledTask.run();
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

    // must be called under LOCK
    private void schedule() {
      myFuture = myExecutorService.schedule(this, myDelay, TimeUnit.MILLISECONDS);
    }

    /**
     * @return task if not yet executed
     */
    @Nullable
    private Runnable cancel() {
      synchronized (LOCK) {
        Future<?> future = myFuture;
        if (future != null) {
          future.cancel(false);
          myFuture = null;
        }
        Runnable task = myTask;
        myTask = null;
        return task;
      }
    }

    @Override
    public String toString() {
      synchronized (LOCK) {
        Runnable task = myTask;
        return super.toString() + (task != null ? ": "+task : "");
      }
    }
  }

  @NotNull
  public Alarm setActivationComponent(@NotNull final JComponent component) {
    myActivationComponent = component;
    //noinspection ResultOfObjectAllocationIgnored
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
}
