// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.EdtInvocationManager;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public final class LaterInvocator {
  private static final Logger LOG = Logger.getInstance(LaterInvocator.class);

  private LaterInvocator() { }

  // Application modal entities
  private static final List<Object> ourModalEntities = ContainerUtil.createLockFreeCopyOnWriteList();

  // Per-project modal entities
  private static final Map<Project, List<Dialog>> projectToModalEntities = ContainerUtil.createWeakMap(); // accessed in EDT only
  private static final Map<Project, Stack<ModalityState>> projectToModalEntitiesStack = ContainerUtil.createWeakMap(); // accessed in EDT only
  private static final Stack<ModalityStateEx> ourModalityStack = new Stack<>((ModalityStateEx)ModalityState.NON_MODAL);// guarded by ourModalityStack
  private static final EventDispatcher<ModalityStateListener> ourModalityStateMulticaster =
    EventDispatcher.create(ModalityStateListener.class);
  private static final FlushQueue ourEdtQueue = new FlushQueue();

  public static void addModalityStateListener(@NotNull ModalityStateListener listener, @NotNull Disposable parentDisposable) {
    if (!ourModalityStateMulticaster.getListeners().contains(listener)) {
      ourModalityStateMulticaster.addListener(listener, parentDisposable);
    }
  }

  private static final ConcurrentMap<Window, ModalityStateEx> ourWindowModalities = CollectionFactory.createConcurrentWeakMap();

  @NotNull
  static ModalityStateEx modalityStateForWindow(@NotNull Window window) {
    return ourWindowModalities.computeIfAbsent(window, __ -> {
      synchronized (ourModalityStack) {
        for (ModalityStateEx state : ourModalityStack) {
          if (state.contains(window)) {
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

  static void invokeLater(@NotNull ModalityState modalityState,
                          @NotNull Condition<?> expired,
                          @NotNull Runnable runnable) {
    if (expired.value(null)) {
      return;
    }
    FlushQueue.RunnableInfo runnableInfo = new FlushQueue.RunnableInfo(runnable, modalityState, expired);
    ourEdtQueue.push(runnableInfo);
  }

  static void invokeAndWait(@NotNull ModalityState modalityState, @NotNull final Runnable runnable) {
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread());

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
    invokeLater(modalityState, Conditions.alwaysFalse(), runnable1);
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
      ModalityStateEx current = state;
      state = modalityStateForWindow((Window)modalEntity);
      state.forceModalEntities(current);
    }
    enterModal(modalEntity, state);
  }

  public static void enterModal(@NotNull Object modalEntity, @NotNull ModalityStateEx appendedState) {
    assertIsDispatchThread();

    if (LOG.isDebugEnabled()) {
      LOG.debug("enterModal:" + modalEntity);
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(true, modalEntity);

    ourModalEntities.add(modalEntity);
    synchronized (ourModalityStack) {
      ourModalityStack.push(appendedState);
    }

    TransactionGuardImpl guard = LoadingState.COMPONENTS_LOADED.isOccurred() ? (TransactionGuardImpl)TransactionGuard.getInstance() : null;
    if (guard != null) {
      guard.enteredModality(appendedState);
    }

    reincludeSkippedItemsAndRequestFlush();
  }

  public static void enterModal(@NotNull Project project, @NotNull Dialog dialog) {
    assertIsDispatchThread();

    if (LOG.isDebugEnabled()) {
      LOG.debug("enterModal:" + dialog.getName() + " ; for project: " + project.getName());
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(true, dialog);

    List<Dialog> modalEntitiesList = projectToModalEntities.computeIfAbsent(project, __->ContainerUtil.createLockFreeCopyOnWriteList());
    modalEntitiesList.add(dialog);

    Stack<ModalityState> modalEntitiesStack = projectToModalEntitiesStack.computeIfAbsent(project, __->new Stack<>(ModalityState.NON_MODAL));
    modalEntitiesStack.push(new ModalityStateEx(ourModalEntities));
  }

  /**
   * Marks the given modality state (not {@code any()}) as transparent, i.e. {@code invokeLater} calls with its "parent" modality state
   * will also be executed within it. NB: this will cause all VFS/PSI/etc. events be executed inside your modal dialog, so you'll need
   * to handle them appropriately, so please consider making the dialog non-modal instead of using this API.
   */
  @ApiStatus.Internal
  public static void markTransparent(@NotNull ModalityState state) {
    assertIsDispatchThread();
    ((ModalityStateEx)state).markTransparent();
    reincludeSkippedItemsAndRequestFlush();
  }

  public static void leaveModal(Project project, @NotNull Dialog dialog) {
    assertIsDispatchThread();

    if (LOG.isDebugEnabled()) {
      LOG.debug("leaveModal:" + dialog.getName() + " ; for project: " + project.getName());
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(false, dialog);

    int index = ourModalEntities.indexOf(dialog);

    if (index != -1) {
      removeModality(dialog, index);
    }
    else if (project != null) {
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

    reincludeSkippedItemsAndRequestFlush();
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


  public static void leaveModal(@NotNull Object modalEntity) {
    assertIsDispatchThread();

    if (LOG.isDebugEnabled()) {
      LOG.debug("leaveModal:" + modalEntity);
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(false, modalEntity);

    int index = ourModalEntities.indexOf(modalEntity);
    LOG.assertTrue(index >= 0);
    removeModality(modalEntity, index);

    reincludeSkippedItemsAndRequestFlush();
  }

  @TestOnly
  public static void leaveAllModals() {
    assertIsDispatchThread();
    while (!ourModalEntities.isEmpty()) {
      leaveModal(ourModalEntities.get(ourModalEntities.size() - 1));
    }
    LOG.assertTrue(getCurrentModalityState() == ModalityState.NON_MODAL, getCurrentModalityState());
    reincludeSkippedItemsAndRequestFlush();
  }

  /**
   * This method attempts to cancel all current modal entities.
   * This may cause memory leaks from improperly closed/undisposed modal dialogs.
   * Intended for use mostly in Remote Dev where forcefully leaving all modalities is better than alternatives.
   */
  @ApiStatus.Internal
  public static void forceLeaveAllModals() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ModalityStateEx currentState = getCurrentModalityState();
    if (currentState != ModalityState.NON_MODAL) {
      currentState.cancelAllEntities();
      // let event queue pump once before trying to cancel next modal
      invokeLater(ModalityState.any(), Conditions.alwaysFalse(), () -> forceLeaveAllModals());
    }
  }

  public static Object @NotNull [] getCurrentModalEntities() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return ArrayUtil.toObjectArray(ourModalEntities);
  }

  @NotNull
  public static ModalityStateEx getCurrentModalityState() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (ourModalityStack) {
      return ourModalityStack.peek();
    }
  }

  public static boolean isInModalContextForProject(@Nullable Project project) {
    assertIsDispatchThread();

    if (ourModalEntities.isEmpty()) return false;
    if (project == null) return true;

    List<Dialog> modalEntitiesForProject = projectToModalEntities.get(project);
    return modalEntitiesForProject == null || modalEntitiesForProject.isEmpty();
  }

  public static boolean isInModalContext() {
    return isInModalContextForProject(null);
  }

  private static void assertIsDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  static boolean isFlushNow(@NotNull Runnable runnable) {
    return ourEdtQueue.isFlushNow(runnable);
  }
  public static void pollWriteThreadEventsOnce() {
    LOG.assertTrue(!SwingUtilities.isEventDispatchThread());
    LOG.assertTrue(ApplicationManager.getApplication().isWriteThread());
  }

  @TestOnly
  @NotNull
  public static Collection<FlushQueue.RunnableInfo> getLaterInvocatorEdtQueue() {
    return ourEdtQueue.getQueue();
  }

  private static void reincludeSkippedItemsAndRequestFlush() {
    assertIsDispatchThread();
    ourEdtQueue.reincludeSkippedItems();
  }

  public static void purgeExpiredItems() {
    assertIsDispatchThread();
    ourEdtQueue.purgeExpiredItems();
  }

  /**
   * @deprecated use {@link com.intellij.testFramework.PlatformTestUtil#dispatchAllEventsInIdeEventQueue()} instead
   */
  @Deprecated
  @TestOnly
  public static void dispatchPendingFlushes() {
    if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Must call from EDT");

    Semaphore semaphore = new Semaphore();
    semaphore.down();
    invokeLater(ModalityState.any(), Conditions.alwaysFalse(), semaphore::up);
    while (!semaphore.isUp()) {
      EdtInvocationManager.dispatchAllInvocationEvents();
    }
  }
}
