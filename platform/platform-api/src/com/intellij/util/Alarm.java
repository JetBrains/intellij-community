// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.Async;
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

  // requests scheduled to myExecutorService
  private final List<Request> myRequests = new SmartList<>(); // guarded by LOCK
  // requests not yet scheduled to myExecutorService (because e.g. corresponding component isn't active yet)
  private final List<Request> myPendingRequests = new SmartList<>(); // guarded by LOCK

  private final ScheduledExecutorService myExecutorService;

  private final Object LOCK = new Object();
  final ThreadToUse myThreadToUse;

  private JComponent myActivationComponent;

  @Override
  public void dispose() {
    if (!myDisposed) {
      myDisposed = true;
      cancelAllRequests();

      if (myExecutorService != EdtExecutorService.getScheduledExecutorInstance()) {
        myExecutorService.shutdownNow();
      }
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
  }

  public Alarm(@NotNull ThreadToUse threadToUse, @Nullable Disposable parentDisposable) {
    myThreadToUse = threadToUse;

    myExecutorService = threadToUse == ThreadToUse.SWING_THREAD ?
                        // pass straight to EDT
                        EdtExecutorService.getScheduledExecutorInstance() :

                        // or pass to app pooled thread.
                        // have to restrict the number of running tasks because otherwise the (implicit) contract of
                        // "addRequests with the same delay are executed in order" will be broken
                        AppExecutorUtil.createBoundedScheduledExecutorService("Alarm Pool", 1);

    if (parentDisposable == null) {
      if (threadToUse == ThreadToUse.POOLED_THREAD || threadToUse != ThreadToUse.SWING_THREAD) {
        boolean crash = threadToUse == ThreadToUse.POOLED_THREAD || ApplicationManager.getApplication().isUnitTestMode();
        IllegalArgumentException t = new IllegalArgumentException("You must provide parent Disposable for non-swing thread Alarm");
        if (crash) {
          throw t;
        }
        // do not crash yet in case of deprecated SHARED_THREAD
        LOG.warn(t);
      }
    }
    else {
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
    return application.getDefaultModalityState();
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

  // returns number of requests canceled
  public int cancelAllRequests() {
    synchronized (LOCK) {
      return cancelAllRequests(myRequests) +
      cancelAllRequests(myPendingRequests);
    }
  }

  private int cancelAllRequests(@NotNull List<Request> list) {
    int count = list.size();
    for (Request request : list) {
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

      requests = new SmartList<>();
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
    UIUtil.dispatchAllInvocationEvents();
  }

  /**
   * wait for all requests to start execution (i.e. their delay elapses and their run() method, well, runs)
   * and then wait for the execution to finish.
   */
  @TestOnly
  public void waitForAllExecuted(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    List<Request> requests;
    synchronized (LOCK) {
      requests = new ArrayList<>(myRequests);
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

    @Async.Schedule
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
            }
            if (myThreadToUse == ThreadToUse.SWING_THREAD && !isEdt()) {
              //noinspection SSBasedInspection
              EdtInvocationManager.getInstance().invokeLater(() -> runSafely(task));
            }
            else {
              runSafely(task);
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
          else {
            app.invokeLater(scheduledTask, myModalityState);
          }
        }
      }
      catch (ProcessCanceledException ignored) { }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    @Async.Execute
    private void runSafely(@Nullable Runnable task) {
      try {
        if (!myDisposed && task != null) {
          QueueProcessor.runSafely(task);
        }
      }
      finally {
        // remove from the list after execution to be able for waitForAllExecuted() to wait for completion
        synchronized (LOCK) {
          myRequests.remove(this);
          myFuture = null;
        }
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
      Runnable task;
      synchronized (LOCK) {
        task = myTask;
      }
      return super.toString() + (task != null ? ": "+task : "");
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
