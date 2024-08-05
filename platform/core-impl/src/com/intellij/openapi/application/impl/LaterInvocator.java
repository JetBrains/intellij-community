// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.diagnostic.LoadingState;
import com.intellij.model.SideEffectGuard;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Ref;
import com.intellij.util.*;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@ApiStatus.Internal
public final class LaterInvocator {
  private static final Logger LOG = Logger.getInstance(LaterInvocator.class);

  private LaterInvocator() { }

  // Application modal entities
  private static final List<Object> ourModalEntities = ContainerUtil.createLockFreeCopyOnWriteList();

  // Per-project modal entities
  private static final Map<Project, List<Dialog>> projectToModalEntities = new WeakHashMap<>(); // accessed in EDT only
  private static final Map<Project, Stack<ModalityState>> projectToModalEntitiesStack = new WeakHashMap<>(); // accessed in EDT only
  private static final Stack<ModalityStateEx> ourModalityStack = new Stack<>((ModalityStateEx)ModalityState.nonModal());// guarded by ourModalityStack
  private static final EventDispatcher<ModalityStateListener> ourModalityStateMulticaster =
    EventDispatcher.create(ModalityStateListener.class);
  private static final FlushQueue ourEdtQueue = new FlushQueue();

  public static void addModalityStateListener(@NotNull ModalityStateListener listener, @NotNull Disposable parentDisposable) {
    if (!ourModalityStateMulticaster.getListeners().contains(listener)) {
      ourModalityStateMulticaster.addListener(listener, parentDisposable);
    }
  }

  private static final ConcurrentMap<Window, ModalityStateEx> ourWindowModalities = CollectionFactory.createConcurrentWeakMap();

  static @NotNull ModalityStateEx modalityStateForWindow(@NotNull Window window) {
    return ourWindowModalities.computeIfAbsent(window, __ -> {
      synchronized (ourModalityStack) {
        for (ModalityStateEx state : ourModalityStack) {
          if (state.contains(window)) {
            return state;
          }
        }
      }

      Window owner = window.getOwner();
      ModalityStateEx ownerState = owner == null ? (ModalityStateEx)ModalityState.nonModal() : modalityStateForWindow(owner);
      return isModalDialog(window) ? ownerState.appendEntity(window) : ownerState;
    });
  }

  private static boolean isModalDialog(@NotNull Object window) {
    return window instanceof Dialog && ((Dialog)window).isModal();
  }

  static void invokeLater(@NotNull ModalityState modalityState,
                          @NotNull Condition<?> expired,
                          @NotNull Runnable runnable) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.INVOKE_LATER);
    if (expired.value(null)) {
      return;
    }
    ourEdtQueue.push(modalityState, expired, runnable);
  }

  static void invokeAndWait(@NotNull ModalityState modalityState, final @NotNull Runnable runnable) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();

    final AtomicReference<Runnable> runnableRef = new AtomicReference<>(runnable);
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final Ref<Throwable> exception = Ref.create();
    Runnable runnable1 = new Runnable() {
      @Override
      public void run() {
        Runnable runnable = runnableRef.getAndSet(null);
        if (runnable == null) {
          return;
        }
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
      public @NonNls String toString() {
        Runnable runnable = runnableRef.get();
        return "InvokeAndWait[" + (runnable == null ? "(cancelled)" : runnable.toString()) + "]";
      }
    };
    invokeLater(modalityState, Conditions.alwaysFalse(), runnable1);
    try {
      while (!semaphore.waitFor(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)) {
        ProgressManager.checkCanceled();
      }
    }
    catch (Throwable e) {
      runnableRef.set(null);
      throw e;
    }
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
    ThreadingAssertions.assertEventDispatchThread();

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
    ThreadingAssertions.assertEventDispatchThread();

    if (LOG.isDebugEnabled()) {
      LOG.debug("enterModal:" + dialog.getName() + " ; for project: " + project.getName());
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(true, dialog);

    List<Dialog> modalEntitiesList = projectToModalEntities.computeIfAbsent(project, __->ContainerUtil.createLockFreeCopyOnWriteList());
    modalEntitiesList.add(dialog);

    Stack<ModalityState> modalEntitiesStack = projectToModalEntitiesStack.computeIfAbsent(project, __->new Stack<>(ModalityState.nonModal()));
    modalEntitiesStack.push(new ModalityStateEx(ourModalEntities));
  }

  /**
   * Marks the given modality state (not {@code any()}) as transparent, i.e. {@code invokeLater} calls with its "parent" modality state
   * will also be executed within it. NB: this will cause all VFS/PSI/etc. events be executed inside your modal dialog, so you'll need
   * to handle them appropriately, so please consider making the dialog non-modal instead of using this API.
   *
   * @deprecated this makes unrelated dialogs appear on top of each other.
   * If the dialog modality is transparent, then the dialog should not be modal in the first place.
   */
  @ApiStatus.Internal
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static void markTransparent(@NotNull ModalityState state) {
    ThreadingAssertions.assertEventDispatchThread();
    ((ModalityStateEx)state).markTransparent();
    reincludeSkippedItemsAndRequestFlush();
  }

  public static void leaveModal(Project project, @NotNull Dialog dialog) {
    ThreadingAssertions.assertEventDispatchThread();

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
    ThreadingAssertions.assertEventDispatchThread();

    if (LOG.isDebugEnabled()) {
      LOG.debug("leaveModal:" + modalEntity);
    }

    Cancellation.executeInNonCancelableSection(() -> {
      ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(false, modalEntity);

      int index = ourModalEntities.indexOf(modalEntity);
      LOG.assertTrue(index >= 0);
      removeModality(modalEntity, index);

      reincludeSkippedItemsAndRequestFlush();
    });
  }

  @TestOnly
  public static void leaveAllModals() {
    ThreadingAssertions.assertEventDispatchThread();
    while (!ourModalEntities.isEmpty()) {
      leaveModal(ourModalEntities.get(ourModalEntities.size() - 1));
    }
    LOG.assertTrue(getCurrentModalityState() == ModalityState.nonModal(), getCurrentModalityState());
    reincludeSkippedItemsAndRequestFlush();
  }

  /**
   * This method attempts to cancel all current modal entities.
   * This may cause memory leaks from improperly closed/undisposed modal dialogs.
   * Intended for use mostly in Remote Dev where forcefully leaving all modalities is better than alternatives.
   */
  @ApiStatus.Internal
  public static void forceLeaveAllModals() {
    ThreadingAssertions.assertEventDispatchThread();
    ModalityStateEx currentState = getCurrentModalityState();
    if (currentState != ModalityState.nonModal()) {
      currentState.cancelAllEntities();
      // let event queue pump once before trying to cancel next modal
      invokeLater(ModalityState.any(), Conditions.alwaysFalse(), () -> forceLeaveAllModals());
    }
  }

  public static Object @NotNull [] getCurrentModalEntities() {
    ThreadingAssertions.assertEventDispatchThread();
    return ArrayUtil.toObjectArray(ourModalEntities);
  }

  public static @NotNull ModalityStateEx getCurrentModalityState() {
    ThreadingAssertions.assertEventDispatchThread();
    synchronized (ourModalityStack) {
      return ourModalityStack.peek();
    }
  }

  public static boolean isInModalContextForProject(@Nullable Project project) {
    ThreadingAssertions.assertEventDispatchThread();

    if (ourModalEntities.isEmpty()) return false;
    if (project == null) return true;

    List<Dialog> modalEntitiesForProject = projectToModalEntities.get(project);
    return modalEntitiesForProject == null || modalEntitiesForProject.isEmpty();
  }

  public static boolean isInModalContext() {
    return isInModalContextForProject(null);
  }

  public static boolean isInModalContext(@NotNull JFrame frame, @Nullable Project project) {
    Object[] entities = getCurrentModalEntities();
    int forOtherProjects = 0;

    for (Object entity : entities) {
      if (entity instanceof ModalContextProjectLocator && !((ModalContextProjectLocator)entity).isPartOf(frame, project)) {
        forOtherProjects++;
      }
      else if (entity instanceof Component && !isAncestor(frame, (Component)entity)) {
        forOtherProjects++;
      }
    }
    if (forOtherProjects == entities.length) {
      return false;
    }
    return true;
  }

  private static boolean isAncestor(@NotNull Component ancestor, @Nullable Component descendant) {
    while (descendant != null) {
      if (descendant == ancestor) {
        return true;
      }
      descendant = descendant.getParent();
    }
    return false;
  }

  static boolean isFlushNow(@NotNull Runnable runnable) {
    return ourEdtQueue.isFlushNow(runnable);
  }
  public static void pollWriteThreadEventsOnce() {
    LOG.assertTrue(!SwingUtilities.isEventDispatchThread());
    LOG.assertTrue(ApplicationManager.getApplication().isWriteIntentLockAcquired());
  }

  @TestOnly
  public static @NotNull Object getLaterInvocatorEdtQueue() {
    return ourEdtQueue.getQueue();
  }

  private static void reincludeSkippedItemsAndRequestFlush() {
    ThreadingAssertions.assertEventDispatchThread();
    ourEdtQueue.reincludeSkippedItems();
  }

  public static void purgeExpiredItems() {
    ThreadingAssertions.assertEventDispatchThread();
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
      EDT.dispatchAllInvocationEvents();
    }
  }
}
