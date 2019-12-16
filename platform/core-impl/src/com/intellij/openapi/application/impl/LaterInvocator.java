// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.diagnostic.EventsWatcher;
import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class LaterInvocator {
  private static final Logger LOG = Logger.getInstance(LaterInvocator.class);
  private static final boolean DEBUG = LOG.isDebugEnabled();

  private static final Object LOCK = new Object();

  private LaterInvocator() { }

  private static class RunnableInfo {
    @NotNull private final Runnable runnable;
    @NotNull private final ModalityState modalityState;
    @NotNull private final Condition<?> expired;
    @Nullable private final ActionCallback callback;
    private final boolean onEdt;

    @Async.Schedule
    RunnableInfo(@NotNull Runnable runnable,
                 @NotNull ModalityState modalityState,
                 @NotNull Condition<?> expired,
                 @Nullable ActionCallback callback,
                 boolean edt) {
      this.runnable = runnable;
      this.modalityState = modalityState;
      this.expired = expired;
      this.callback = callback;
      onEdt = edt;
    }

    void markDone() {
      if (callback != null) callback.setDone();
    }

    @Override
    @NonNls
    public String toString() {
      return "[runnable: " + runnable + "; state=" + modalityState + (expired.value(null) ? "; expired" : "")+"] ";
    }
  }

  // Application modal entities
  private static final List<Object> ourModalEntities = ContainerUtil.createLockFreeCopyOnWriteList();

  // Per-project modal entities
  private static final Map<Project, List<Dialog>> projectToModalEntities = ContainerUtil.createWeakMap();
  private static final Map<Project, Stack<ModalityState>> projectToModalEntitiesStack = ContainerUtil.createWeakMap();

  private static final Stack<ModalityStateEx> ourModalityStack = new Stack<>((ModalityStateEx)ModalityState.NON_MODAL);
  private static final List<RunnableInfo> ourSkippedItems = new ArrayList<>(); //protected by LOCK
  private static final ArrayDeque<RunnableInfo> ourQueue = new ArrayDeque<>(); //protected by LOCK
  private static final ArrayDeque<RunnableInfo> ourLegacyQueue = new ArrayDeque<>(); //protected by LOCK
  private static final FlushQueue ourFlushQueueRunnable = new FlushQueue();
  private static final Executor ourWriteThreadExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Write Thread", 1);

  private static final EventDispatcher<ModalityStateListener> ourModalityStateMulticaster = EventDispatcher.create(ModalityStateListener.class);

  public static void addModalityStateListener(@NotNull ModalityStateListener listener, @NotNull Disposable parentDisposable) {
    if (!ourModalityStateMulticaster.getListeners().contains(listener)) {
      ourModalityStateMulticaster.addListener(listener, parentDisposable);
    }
  }

  private static final ConcurrentMap<Window, ModalityStateEx> ourWindowModalities = ContainerUtil.createConcurrentWeakMap();

  @NotNull
  static ModalityStateEx modalityStateForWindow(@NotNull Window window) {
    return ourWindowModalities.computeIfAbsent(window, __ -> {
      synchronized (ourModalityStack) {
        for (ModalityStateEx state : ourModalityStack) {
          if (state.getModalEntities().contains(window)) {
            return state;
          }
        }
      }

      Window owner = window.getOwner();
      ModalityStateEx ownerState = owner == null ? (ModalityStateEx)ModalityState.NON_MODAL : modalityStateForWindow(owner);
      return isModalDialog(window) ? ownerState.appendEntity(window) : ownerState;
    });
  }

  private static boolean isModalDialog(@NotNull Object window) {
    return window instanceof Dialog && ((Dialog)window).isModal();
  }

  @NotNull
  static ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull Condition<?> expired, boolean onEdt) {
    ModalityState modalityState = ModalityState.defaultModalityState();
    return invokeLater(runnable, modalityState, expired, onEdt);
  }

  @NotNull
  static ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState modalityState, boolean onEdt) {
    return invokeLater(runnable, modalityState, Conditions.alwaysFalse(), onEdt);
  }

  @NotNull
  static ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState modalityState, @NotNull Condition<?> expired, boolean onEdt) {
    ActionCallback callback = new ActionCallback();
    invokeLaterWithCallback(runnable, modalityState, expired, callback, onEdt);
    return callback;
  }

  static void invokeLaterWithCallback(@NotNull Runnable runnable, @NotNull ModalityState modalityState, @NotNull Condition<?> expired, @Nullable ActionCallback callback,
                                      boolean onEdt) {
    if (expired.value(null)) {
      if (callback != null) {
        callback.setRejected();
      }
      return;
    }
    RunnableInfo runnableInfo = new RunnableInfo(runnable, modalityState, expired, callback, onEdt);
    synchronized (LOCK) {
      ArrayDeque<RunnableInfo> queue = onEdt ? ourLegacyQueue : ourQueue;
      queue.add(runnableInfo);
    }
    requestFlush(onEdt);
  }

  static void invokeAndWait(@NotNull final Runnable runnable, @NotNull ModalityState modalityState, boolean onEdt) {
    LOG.assertTrue(!isDispatchThread());

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final Ref<Throwable> exception = Ref.create();
    Runnable runnable1 = new Runnable() {
      @Override
      public void run() {
        try {
          runnable.run();
        }
        catch (Throwable e) {
          exception.set(e);
        }
        finally {
          semaphore.up();
        }
      }

      @Override
      @NonNls
      public String toString() {
        return "InvokeAndWait[" + runnable + "]";
      }
    };
    invokeLaterWithCallback(runnable1, modalityState, Conditions.alwaysFalse(), null, onEdt);
    semaphore.waitFor();
    if (!exception.isNull()) {
      Throwable cause = exception.get();
      if (SystemProperties.getBooleanProperty("invoke.later.wrap.error", true)) {
        // wrap everything to keep the current thread stacktrace
        // also TC ComparisonFailure feature depends on this
        throw new RuntimeException(cause);
      }
      else {
        ExceptionUtil.rethrow(cause);
      }
    }
  }

  public static void enterModal(@NotNull Object modalEntity) {
    ModalityStateEx state = getCurrentModalityState().appendEntity(modalEntity);
    if (isModalDialog(modalEntity)) {
      List<Object> currentEntities = state.getModalEntities();
      state = modalityStateForWindow((Window)modalEntity);
      state.forceModalEntities(currentEntities);
    }
    enterModal(modalEntity, state);
  }

  public static void enterModal(@NotNull Object modalEntity, @NotNull ModalityStateEx appendedState) {
    LOG.assertTrue(isDispatchThread(), "enterModal() should be invoked in event-dispatch thread");

    if (LOG.isDebugEnabled()) {
      LOG.debug("enterModal:" + modalEntity);
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(true);

    ourModalEntities.add(modalEntity);
    synchronized (ourModalityStack) {
      ourModalityStack.push(appendedState);
    }

    TransactionGuardImpl guard = LoadingState.COMPONENTS_LOADED.isOccurred() ? (TransactionGuardImpl)TransactionGuard.getInstance() : null;
    if (guard != null) {
      guard.enteredModality(appendedState);
    }

    reincludeSkippedItems();
    requestFlushAll();
  }

  public static void enterModal(Project project, Dialog dialog) {
    LOG.assertTrue(isDispatchThread(), "enterModal() should be invoked in event-dispatch thread");

    if (LOG.isDebugEnabled()) {
      LOG.debug("enterModal:" + dialog.getName() + " ; for project: " + project.getName());
    }

    if (project == null) {
      enterModal(dialog);
      return;
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(true);

    List<Dialog> modalEntitiesList = projectToModalEntities.getOrDefault(project, ContainerUtil.createLockFreeCopyOnWriteList());
    projectToModalEntities.put(project, modalEntitiesList);
    modalEntitiesList.add(dialog);

    Stack<ModalityState> modalEntitiesStack = projectToModalEntitiesStack.getOrDefault(project, new Stack<>(ModalityState.NON_MODAL));
    projectToModalEntitiesStack.put(project, modalEntitiesStack);
    modalEntitiesStack.push(new ModalityStateEx(ArrayUtil.toObjectArray(ourModalEntities)));
  }

  /**
   * Marks the given modality state (not {@code any()}} as transparent, i.e. {@code invokeLater} calls with its "parent" modality state
   * will also be executed within it. NB: this will cause all VFS/PSI/etc events be executed inside your modal dialog, so you'll need
   * to handle them appropriately, so please consider making the dialog non-modal instead of using this API.
   */
  @ApiStatus.Internal
  public static void markTransparent(@NotNull ModalityState state) {
    ((ModalityStateEx)state).markTransparent();
    reincludeSkippedItems();
    requestFlush(true);
  }

  public static void leaveModal(Project project, Dialog dialog) {
    LOG.assertTrue(isDispatchThread(), "leaveModal() should be invoked in event-dispatch thread");

    if (LOG.isDebugEnabled()) {
      LOG.debug("leaveModal:" + dialog.getName() + " ; for project: " + project.getName());
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(false);

    int index = ourModalEntities.indexOf(dialog);

    if (index != -1) {
      removeModality(dialog, index);
    } else if (project != null) {
      List<Dialog> dialogs = projectToModalEntities.get(project);
      int perProjectIndex = dialogs.indexOf(dialog);
      LOG.assertTrue(perProjectIndex >= 0);
      dialogs.remove(perProjectIndex);
      Stack<ModalityState> states = projectToModalEntitiesStack.get(project);
      states.remove(perProjectIndex + 1);
      for (int i = 1; i < states.size(); i++) {
        ((ModalityStateEx)states.get(i)).removeModality(dialog);
      }
    }

    reincludeSkippedItems();
    requestFlushAll();
  }

  private static void removeModality(@NotNull Object modalEntity, int index) {
    ourModalEntities.remove(index);
    synchronized (ourModalityStack) {
      ourModalityStack.remove(index + 1);
      for (int i = 1; i < ourModalityStack.size(); i++) {
        ourModalityStack.get(i).removeModality(modalEntity);
      }
    }
    ModalityStateEx.unmarkTransparent(modalEntity);
  }

  private static void reincludeSkippedItems() {
    synchronized (LOCK) {
      for (int i = ourSkippedItems.size() - 1; i >= 0; i--) {
        RunnableInfo item = ourSkippedItems.get(i);
        ArrayDeque<RunnableInfo> queue = item.onEdt ? ourLegacyQueue : ourQueue;
        queue.addFirst(item);
      }
      ourSkippedItems.clear();
    }
  }

  public static void leaveModal(@NotNull Object modalEntity) {
    LOG.assertTrue(isDispatchThread(), "leaveModal() should be invoked in event-dispatch thread");

    if (LOG.isDebugEnabled()) {
      LOG.debug("leaveModal:" + modalEntity);
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(false);

    int index = ourModalEntities.indexOf(modalEntity);
    LOG.assertTrue(index >= 0);
    removeModality(modalEntity, index);

    reincludeSkippedItems();
    requestFlushAll();
  }

  @TestOnly
  public static void leaveAllModals() {
    while (!ourModalEntities.isEmpty()) {
      leaveModal(ourModalEntities.get(ourModalEntities.size() - 1));
    }
    LOG.assertTrue(getCurrentModalityState() == ModalityState.NON_MODAL, getCurrentModalityState());
    reincludeSkippedItems();
    requestFlushAll();
  }

  public static Object @NotNull [] getCurrentModalEntities() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return ArrayUtil.toObjectArray(ourModalEntities);
  }

  @NotNull
  public static ModalityStateEx getCurrentModalityState() {
    if (!SwingUtilities.isEventDispatchThread()) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    synchronized (ourModalityStack) {
      return ourModalityStack.peek();
    }
  }

  public static boolean isInModalContextForProject(final Project project) {
    LOG.assertTrue(isDispatchThread());

    if (ourModalEntities.isEmpty()) return false;

    List<Dialog> modalEntitiesForProject = projectToModalEntities.get(project);

    return modalEntitiesForProject == null || modalEntitiesForProject.isEmpty();
  }

  public static boolean isInModalContext() {
    return isInModalContextForProject(null);
  }

  private static boolean isDispatchThread() {
    return ApplicationManager.getApplication().isDispatchThread();
  }

  private static void requestFlush(boolean onEdt) {
    int currentValue = FLUSHER_SCHEDULED.getAndUpdate(value -> value | (1 << (onEdt ? 0 : 1)) | (1 << 3));
    submitFlush(currentValue);
  }

  private static void requestFlushAll() {
    int currentValue = FLUSHER_SCHEDULED.getAndUpdate(value -> value | 11);
    submitFlush(currentValue);
  }

  private static void continueFlush() {
    int currentValue = FLUSHER_SCHEDULED.getAndUpdate(value -> (value & 3) > 0 ? value | (1 << 3) : value);
    if ((currentValue & 3) > 0) {
      submitFlush(currentValue);
    }
  }

  private static void submitFlush(int currentValue) {
    if (currentValue >> 3 == 0) {
      if ((currentValue >> 2 & 1) == 0) {
        SwingUtilities.invokeLater(ourFlushQueueRunnable);
      }
      else {
        ourWriteThreadExecutor
          .execute(() -> ApplicationManager.getApplication().runIntendedWriteActionOnCurrentThread(ourFlushQueueRunnable));
      }
    }
  }

  public static void pollWriteThreadEventsOnce() {
    LOG.assertTrue(!SwingUtilities.isEventDispatchThread());
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    FLUSHER_SCHEDULED.getAndUpdate(value -> value | (1 << 2));
    ourFlushQueueRunnable.run();
  }

  /**
   * There might be some requests in the queue, but ourFlushQueueRunnable might not be scheduled yet. In these circumstances
   * {@link EventQueue#peekEvent()} default implementation would return null, and {@link UIUtil#dispatchAllInvocationEvents()} would
   * stop processing events too early and lead to spurious test failures.
   *
   * @see IdeEventQueue#peekEvent()
   */
  public static boolean ensureFlushRequested() {
    if (getNextEvent(ourLegacyQueue, false) != null) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(ourFlushQueueRunnable);
      return true;
    }
    return false;
  }

  @Nullable
  private static RunnableInfo getNextEvent(ArrayDeque<RunnableInfo> queue, boolean remove) {
    synchronized (LOCK) {
      ModalityState currentModality = getCurrentModalityState();

      while (!queue.isEmpty()) {
        RunnableInfo info = queue.getFirst();

        if (info.expired.value(null)) {
          queue.removeFirst();
          info.markDone();
          continue;
        }

        if (!currentModality.dominates(info.modalityState)) {
          if (remove) {
            queue.removeFirst();
          }
          return info;
        }
        ourSkippedItems.add(queue.removeFirst());
      }

      return null;
    }
  }

  // bits: 0 — need to update EDT, 1 — need to update WT, 2 — current queue to be flushed, 3 — is update queued
  private static final AtomicInteger FLUSHER_SCHEDULED = new AtomicInteger(0);

  private static class FlushQueue implements Runnable {
    private RunnableInfo myLastInfo;

    @Override
    public void run() {
      int whichThread = FLUSHER_SCHEDULED.getAndUpdate(current -> {
        return (current & 7 & ~(1 << (current >> 2 & 1))) ^ 4;
      }) >> 2 & 1;

      ArrayDeque<RunnableInfo> queue = whichThread == 0 ? ourLegacyQueue : ourQueue;
      if ((whichThread == 0) ^ (SwingUtilities.isEventDispatchThread())) {
        LOG.error("Tasks for one thread are executed on another");
      }

      long startTime = System.currentTimeMillis();
      while (true) {
        if (!runNextEvent(queue)) {
          break;
        }
        if (System.currentTimeMillis() - startTime > 5) {
          requestFlush(whichThread == 0);
          break;
        }
      }
      continueFlush();
    }

    private boolean runNextEvent(ArrayDeque<RunnableInfo> queue) {
      long startedAt = System.currentTimeMillis();
      final RunnableInfo lastInfo = getNextEvent(queue, true);
      myLastInfo = lastInfo;

      if (lastInfo != null) {
        EventsWatcher watcher = EventsWatcher.getInstance();
        Runnable runnable = lastInfo.runnable;
        if (watcher != null) {
          watcher.runnableStarted(runnable);
        }

        try {
          doRun(lastInfo);
          lastInfo.markDone();
        }
        catch (ProcessCanceledException ignored) {

        }
        catch (Throwable t) {
          if (ApplicationManager.getApplication().isUnitTestMode()) {
            ExceptionUtil.rethrow(t);
          }
          LOG.error(t);
        }
        finally {
          if (!DEBUG) myLastInfo = null;
          if (watcher != null) {
            watcher.runnableFinished(runnable, startedAt);
          }
        }
      }
      return lastInfo != null;
    }

    // Extracted to have a capture point
    private static void doRun(@Async.Execute RunnableInfo info) {
      info.runnable.run();
    }

    @Override
    public String toString() {
      return "LaterInvocator.FlushQueue" + (myLastInfo == null ? "" : " lastInfo=" + myLastInfo);
    }
  }

  @TestOnly
  public static Collection<RunnableInfo> getLaterInvocatorEdtQueue() {
    synchronized (LOCK) {
      // used by leak hunter as root, so we must not copy it here to another list
      // to avoid walking over obsolete queue
      return Collections.unmodifiableCollection(ourLegacyQueue);
    }
  }

  @TestOnly
  @NotNull
  public static Collection<RunnableInfo> getLaterInvocatorWtQueue() {
    synchronized (LOCK) {
      // used by leak hunter as root, so we must not copy it here to another list
      // to avoid walking over obsolete queue
      return Collections.unmodifiableCollection(ourQueue);
    }
  }

  public static void purgeExpiredItems() {
    synchronized (LOCK) {
      reincludeSkippedItems();

      purgeExpiredItems(ourQueue);
      purgeExpiredItems(ourLegacyQueue);
    }
  }

  private static void purgeExpiredItems(ArrayDeque<RunnableInfo> queue) {
    List<RunnableInfo> alive = new ArrayList<>(queue.size());
    for (RunnableInfo info : queue) {
      if (info.expired.value(null)) {
        info.markDone();
      }
      else {
        alive.add(info);
      }
    }
    if (alive.size() < queue.size()) {
      queue.clear();
      queue.addAll(alive);
    }
  }

  @TestOnly
  public static void dispatchPendingFlushes() {
    if (!isDispatchThread()) throw new IllegalStateException("Must call from EDT");

    Semaphore semaphore = new Semaphore();
    semaphore.down();
    invokeLater(semaphore::up, ModalityState.any(), true);
    while (!semaphore.isUp()) {
      UIUtil.dispatchAllInvocationEvents();
    }
  }
}
