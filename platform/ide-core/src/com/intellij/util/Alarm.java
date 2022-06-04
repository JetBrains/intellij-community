// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.codeWithMe.ClientId;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.EdtScheduledExecutorService;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * Allows scheduling `Runnable` instances (requests) to be executed after a specific time interval on a specific thread.
 * Use {@link #addRequest} methods to schedule the requests.
 * Two requests scheduled with the same delay are executed sequentially, one after the other.
 * {@link #cancelAllRequests()} and {@link #cancelRequest(Runnable)} allow canceling already scheduled requests.
 */
public class Alarm implements Disposable {
  protected static final Logger LOG = Logger.getInstance(Alarm.class);

  private volatile boolean myDisposed;

  // requests scheduled to myExecutorService
  private final List<Request> myRequests = new SmartList<>(); // guarded by LOCK
  // requests not yet scheduled to myExecutorService (because e.g. the corresponding component isn't active yet)
  private final List<Request> myPendingRequests = new SmartList<>(); // guarded by LOCK

  private final ScheduledExecutorService myExecutorService;

  private final Object LOCK = new Object();
  private final ThreadToUse myThreadToUse;

  private JComponent myActivationComponent; // accessed in EDT only

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
     * Run request in Swing event dispatch thread; this is the default.
     * NB: <i>Requests shouldn't take long to avoid UI freezes.</i>
     */
    SWING_THREAD,

    /** @deprecated Use {@link #POOLED_THREAD} instead */
    @Deprecated(forRemoval = true)
    SHARED_THREAD,

    /**
     * Run requests in one of application pooled threads.
     *
     * @see Application#executeOnPooledThread(Callable)
     */
    POOLED_THREAD,
  }

  /**
   * Creates an alarm that works in EDT.
   */
  public Alarm() {
    this(ThreadToUse.SWING_THREAD);
  }

  /**
   * Creates an alarm that works in EDT.
   */
  public Alarm(@NotNull Disposable parentDisposable) {
    this(ThreadToUse.SWING_THREAD, parentDisposable);
  }

  public Alarm(@NotNull ThreadToUse threadToUse) {
    this(threadToUse, null);
  }

  /**
   * Creates alarm for EDT which executes its requests only when the {@param activationComponent} is shown on screen
   */
  public Alarm(@NotNull JComponent activationComponent, @NotNull Disposable parent) {
    this(ThreadToUse.SWING_THREAD, parent);
    myActivationComponent = activationComponent;
    //noinspection ResultOfObjectAllocationIgnored
    new UiNotifyConnector(activationComponent, new Activatable() {
      @Override
      public void showNotify() {
        flushPending();
      }
    });
  }

  public Alarm(@NotNull ThreadToUse threadToUse, @Nullable Disposable parentDisposable) {
    myThreadToUse = threadToUse;
    if (threadToUse == ThreadToUse.SHARED_THREAD) {
      PluginException.reportDeprecatedUsage("Alarm.ThreadToUse#SHARED_THREAD", "Please use `POOLED_THREAD` instead");
    }

    myExecutorService = threadToUse == ThreadToUse.SWING_THREAD ?
                        // pass straight to EDT
                        EdtExecutorService.getScheduledExecutorInstance() :

                        // or pass to app pooled thread.
                        // have to restrict the number of running tasks because otherwise the (implicit) contract
                        // "addRequests with the same delay are executed in order" will be broken
                        AppExecutorUtil.createBoundedScheduledExecutorService("Alarm Pool", 1);

    if (parentDisposable == null) {
      if (threadToUse != ThreadToUse.SWING_THREAD) {
        LOG.error(new IllegalArgumentException("You must provide parent Disposable for non-swing thread Alarm"));
      }
    }
    else {
      Disposer.register(parentDisposable, this);
    }
  }

  public void addRequest(@NotNull Runnable request, int delayMillis, boolean runWithActiveFrameOnly) {
    if (runWithActiveFrameOnly && !ApplicationManager.getApplication().isActive()) {
      MessageBus bus = ApplicationManager.getApplication().getMessageBus();
      MessageBusConnection connection = bus.connect(this);
      connection.subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
        @Override
        public void applicationActivated(@NotNull IdeFrame ideFrame) {
          connection.disconnect();
          addRequest(request, delayMillis);
        }
      });
    }
    else {
      addRequest(request, delayMillis);
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

  public void addComponentRequest(@NotNull Runnable request, int delayMillis) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert myActivationComponent != null;
    _addRequest(request, delayMillis, ModalityState.stateForComponent(myActivationComponent));
  }

  public void addComponentRequest(@NotNull Runnable request, long delayMillis) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert myActivationComponent != null;
    _addRequest(request, delayMillis, ModalityState.stateForComponent(myActivationComponent));
  }

  public void addRequest(@NotNull Runnable request, int delayMillis, @Nullable ModalityState modalityState) {
    LOG.assertTrue(myThreadToUse == ThreadToUse.SWING_THREAD);
    _addRequest(request, delayMillis, modalityState);
  }

  public void addRequest(@NotNull Runnable request, long delayMillis, @Nullable ModalityState modalityState) {
    LOG.assertTrue(myThreadToUse == ThreadToUse.SWING_THREAD);
    _addRequest(request, delayMillis, modalityState);
  }

  void cancelAllAndAddRequest(@NotNull Runnable request, int delayMillis, @Nullable ModalityState modalityState) {
    synchronized (LOCK) {
      cancelAllRequests();
      _addRequest(request, delayMillis, modalityState);
    }
  }

  void _addRequest(@NotNull Runnable request, long delayMillis, @Nullable ModalityState modalityState) {
    Request requestToSchedule = new Request(request, modalityState, delayMillis);
    synchronized (LOCK) {
      checkDisposed();
      if (myActivationComponent == null || isActivationComponentShowing()) {
        add(requestToSchedule);
      }
      else if (!myPendingRequests.contains(requestToSchedule)) {
        myPendingRequests.add(requestToSchedule);
      }
    }
  }

  private boolean isActivationComponentShowing() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert myActivationComponent != null;
    return myActivationComponent.isShowing();
  }

  // must be called under LOCK
  private void add(@NotNull Request requestToSchedule) {
    requestToSchedule.schedule();
    myRequests.add(requestToSchedule);
  }

  private void flushPending() {
    synchronized (LOCK) {
      for (Request each : myPendingRequests) {
        add(each);
      }

      myPendingRequests.clear();
    }
  }

  public boolean cancelRequest(@NotNull Runnable request) {
    synchronized (LOCK) {
      cancelAndRemoveRequestFrom(request, myRequests);
      cancelAndRemoveRequestFrom(request, myPendingRequests);
      return true;
    }
  }

  private void cancelAndRemoveRequestFrom(@NotNull Runnable request, @NotNull List<Request> list) {
    for (int i = list.size()-1; i>=0; i--) {
      Request r = list.get(i);
      if (r.myTask == request) {
        r.cancel();
        list.remove(i);
        break;
      }
    }
  }

  // returns number of requests canceled
  public int cancelAllRequests() {
    synchronized (LOCK) {
      return cancelAllRequests(myRequests) + cancelAllRequests(myPendingRequests);
    }
  }

  private int cancelAllRequests(@NotNull List<? extends Request> list) {
    int count = list.size();
    for (Request request : list) {
      request.cancel();
    }
    list.clear();
    return count;
  }

  @TestOnly
  public void drainRequestsInTest() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    for (Runnable task : getUnfinishedRequests()) {
      task.run();
    }
  }

  protected @NotNull List<Runnable> getUnfinishedRequests() {
    List<Runnable> unfinishedTasks;
    synchronized (LOCK) {
      if (myRequests.isEmpty()) {
        return Collections.emptyList();
      }

      unfinishedTasks = new ArrayList<>(myRequests.size());
      for (Request request : myRequests) {
        Runnable existingTask = request.cancel();
        if (existingTask != null) {
          unfinishedTasks.add(existingTask);
        }
      }
      myRequests.clear();
    }
    return unfinishedTasks;
  }

  /**
   * wait for all requests to start execution (i.e. their delay elapses and their run() method, well, runs)
   * and then wait for the execution to finish.
   */
  @TestOnly
  public void waitForAllExecuted(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    assert ApplicationManager.getApplication().isUnitTestMode();

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
        try {
          future.get(timeout, unit);
        }
        catch (CancellationException ignored) {
        }
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

  private class Request implements Runnable {
    private Runnable myTask; // guarded by LOCK
    private final ModalityState myModalityState;
    private Future<?> myFuture; // guarded by LOCK
    private final long myDelayMillis;
    @NotNull
    private final String myClientId;

    @Async.Schedule
    private Request(@NotNull Runnable task, @Nullable ModalityState modalityState, long delayMillis) {
      synchronized (LOCK) {
        myTask = task;

        myModalityState = modalityState;
        myDelayMillis = delayMillis;
        myClientId = ClientId.getCurrentValue();
      }
    }

    @Override
    public void run() {
      try {
        if (myDisposed) {
          return;
        }
        Runnable task;
        synchronized (LOCK) {
          task = myTask;
          myTask = null;
        }
        if (task != null) {
          runSafely(task);
        }
      }
      catch (ProcessCanceledException ignored) { }
    }

    @Async.Execute
    private void runSafely(@Nullable Runnable task) {
      try {
        if (!myDisposed && task != null) {
          try (AccessToken ignored = ClientId.withClientId(myClientId)) {
            QueueProcessor.runSafely(task);
          }
        }
      }
      finally {
        // remove from the list after execution to be able for {@link #waitForAllExecuted(long, TimeUnit)} to wait for completion
        synchronized (LOCK) {
          myRequests.remove(this);
          myFuture = null;
        }
      }
    }

    // must be called under LOCK
    private void schedule() {
      if (myModalityState == null) {
        myFuture = myExecutorService.schedule(this, myDelayMillis, TimeUnit.MILLISECONDS);
      }
      else {
        myFuture = EdtScheduledExecutorService.getInstance().schedule(this, myModalityState, myDelayMillis, TimeUnit.MILLISECONDS);
      }
    }

    /**
     * Must be called under `LOCK`.
     * Returns a task, if not yet executed.
     */
    private @Nullable Runnable cancel() {
      Future<?> future = myFuture;
      if (future != null) {
        if (!future.cancel(false) && !future.isCancelled()) {
          // the future already completed. manifest its errors if any
          try {
            future.get();
          }
          catch (InterruptedException ignored) {
          }
          catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null && !(cause instanceof ControlFlowException)) {
              LOG.error(cause);
            }
          }
        }
        myFuture = null;
      }
      Runnable task = myTask;
      myTask = null;
      return task;
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

  /** @deprecated use {@link #Alarm(JComponent, Disposable)} instead */
  @Deprecated(forRemoval = true)
  public @NotNull Alarm setActivationComponent(@NotNull JComponent component) {
    PluginException.reportDeprecatedUsage("Alarm#setActivationComponent", "Please use `#Alarm(JComponent, Disposable)` instead");
    ApplicationManager.getApplication().assertIsDispatchThread();
    myActivationComponent = component;
    //noinspection ResultOfObjectAllocationIgnored
    new UiNotifyConnector(component, new Activatable() {
      @Override
      public void showNotify() {
        flushPending();
      }
    });
    return this;
  }

  public boolean isDisposed() {
    return myDisposed;
  }
}
