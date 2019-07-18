// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.actions.MaximizeActiveDialogAction;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDManagerImpl;
import com.intellij.ide.plugins.MainRunner;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.FrequentEventDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher;
import com.intellij.openapi.keymap.impl.KeyState;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.FocusManagerImpl;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.mac.touchbar.TouchBarsManager;
import com.intellij.util.Alarm;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AppContext;
import sun.awt.SunToolkit;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class IdeEventQueue extends EventQueue {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.IdeEventQueue");
  private static final Logger TYPEAHEAD_LOG = Logger.getInstance("#com.intellij.ide.IdeEventQueue.typeahead");
  private static final Logger FOCUS_AWARE_RUNNABLES_LOG = Logger.getInstance("#com.intellij.ide.IdeEventQueue.runnables");
  private static final boolean JAVA11_ON_MAC = SystemInfo.isMac && SystemInfo.isJavaVersionAtLeast(11, 0, 0);
  private static TransactionGuardImpl ourTransactionGuard;
  private static ProgressManager ourProgressManager;

  /**
   * Adding/Removing of "idle" listeners should be thread safe.
   */
  private final Object myLock = new Object();

  private final List<Runnable> myIdleListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<Runnable> myActivityListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Alarm myIdleRequestsAlarm = new Alarm();
  private final Map<Runnable, MyFireIdleRequest> myListenerToRequest = new THashMap<>();
  // IdleListener -> MyFireIdleRequest
  private final IdeKeyEventDispatcher myKeyEventDispatcher = new IdeKeyEventDispatcher(this);
  private final IdeMouseEventDispatcher myMouseEventDispatcher = new IdeMouseEventDispatcher();
  private final IdePopupManager myPopupManager = new IdePopupManager();
  private final ToolkitBugsProcessor myToolkitBugsProcessor = new ToolkitBugsProcessor();

  /**
   * Counter of processed events. It is used to assert that data context lives only inside single
   * <p/>
   * Swing event.
   */
  private int myEventCount;
  final AtomicInteger myKeyboardEventsPosted = new AtomicInteger();
  final AtomicInteger myKeyboardEventsDispatched = new AtomicInteger();
  private boolean myIsInInputEvent;
  @NotNull
  private AWTEvent myCurrentEvent = new InvocationEvent(this, EmptyRunnable.getInstance());
  private volatile long myLastActiveTime = System.nanoTime();
  private long myLastEventTime = System.currentTimeMillis();
  private WindowManagerEx myWindowManager;
  private final List<EventDispatcher> myDispatchers = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<EventDispatcher> myPostProcessors = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Set<Runnable> myReady = new THashSet<>();
  private boolean myKeyboardBusy;
  private boolean myWinMetaPressed;
  private int myInputMethodLock;
  private final com.intellij.util.EventDispatcher<PostEventHook>
    myPostEventListeners = com.intellij.util.EventDispatcher.create(PostEventHook.class);

  private final Map<AWTEvent, List<Runnable>> myRunnablesWaitingFocusChange = new THashMap<>();

  public void executeWhenAllFocusEventsLeftTheQueue(@NotNull Runnable runnable) {
    ifFocusEventsInTheQueue(e -> {
      List<Runnable> runnables = myRunnablesWaitingFocusChange.get(e);
      if (runnables != null) {
        if (FOCUS_AWARE_RUNNABLES_LOG.isDebugEnabled()) {
          FOCUS_AWARE_RUNNABLES_LOG.debug("We have already had a runnable for the event: " + e);
        }
        runnables.add(runnable);
      }
      else {
        runnables = new ArrayList<>();
        runnables.add(runnable);
        myRunnablesWaitingFocusChange.put(e, runnables);
      }
    }, runnable);
  }

  @NotNull
  public String runnablesWaitingForFocusChangeState() {
    return StringUtil.join(focusEventsList, event -> "[" + event.getID() + "; "+ event.getSource().getClass().getName()+"]", ", ");
  }

  private void ifFocusEventsInTheQueue(@NotNull Consumer<? super AWTEvent> yes, @NotNull Runnable no) {
    if (!focusEventsList.isEmpty()) {
      if (FOCUS_AWARE_RUNNABLES_LOG.isDebugEnabled()) {
        FOCUS_AWARE_RUNNABLES_LOG.debug("Focus event list (trying to execute runnable): "+runnablesWaitingForFocusChangeState());
      }

      // find the latest focus gained
      AWTEvent first = ContainerUtil.find(focusEventsList, e -> e.getID() == FocusEvent.FOCUS_GAINED);

      if (first != null) {
        if (FOCUS_AWARE_RUNNABLES_LOG.isDebugEnabled()) {
          FOCUS_AWARE_RUNNABLES_LOG
            .debug("    runnable saved for : [" + first.getID() + "; " + first.getSource() + "] -> " + no.getClass().getName());
        }
        yes.accept(first);
      }
      else {
        if (FOCUS_AWARE_RUNNABLES_LOG.isDebugEnabled()) {
          FOCUS_AWARE_RUNNABLES_LOG.debug("    runnable is run on EDT if needed : " + no.getClass().getName());
        }
        UIUtil.invokeLaterIfNeeded(no);
      }
    }
    else {
      if (FOCUS_AWARE_RUNNABLES_LOG.isDebugEnabled()) {
        FOCUS_AWARE_RUNNABLES_LOG.debug("Focus event list is empty: runnable is run right away : " + no.getClass().getName());
      }
      UIUtil.invokeLaterIfNeeded(no);
    }
  }

  private static class IdeEventQueueHolder {
    private static final IdeEventQueue INSTANCE = new IdeEventQueue();
  }

  public static IdeEventQueue getInstance() {
    return IdeEventQueueHolder.INSTANCE;
  }

  private IdeEventQueue() {
    EventQueue systemEventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    assert !(systemEventQueue instanceof IdeEventQueue) : systemEventQueue;
    systemEventQueue.push(this);

    KeyboardFocusManager keyboardFocusManager = IdeKeyboardFocusManager.replaceDefault();
    keyboardFocusManager.addPropertyChangeListener("permanentFocusOwner", e -> {
      final Application application = ApplicationManager.getApplication();
      if (application == null) {
        // We can get focus event before application is initialized
        return;
      }
      application.assertIsDispatchThread();
    });

    addDispatcher(new WindowsAltSuppressor(), null);
    if (SystemInfo.isWin7OrNewer && SystemProperties.getBooleanProperty("keymap.windows.up.to.maximize.dialogs", true)) {
      // 'Windows+Up' shortcut would maximize active dialog under Win 7+
      addDispatcher(new WindowsUpMaximizer(), null);
    }
    addDispatcher(new EditingCanceller(), null);

    abracadabraDaberBoreh();

    IdeKeyEventDispatcher.addDumbModeWarningListener(() -> flushDelayedKeyEvents());

    if (SystemProperties.getBooleanProperty("skip.move.resize.events", true)) {
      myPostEventListeners.addListener(IdeEventQueue::skipMoveResizeEvents);
    }

    ((IdeKeyboardFocusManager)KeyboardFocusManager.getCurrentKeyboardFocusManager()).setTypeaheadHandler(ke -> {
      if (myKeyEventDispatcher.dispatchKeyEvent(ke)) {
        ke.consume();
      }
    });
  }

  private static boolean skipMoveResizeEvents(AWTEvent event) {
    // JList, JTable and JTree paint every cell/row/column using the following method:
    //   CellRendererPane.paintComponent(Graphics, Component, Container, int, int, int, int, boolean)
    // This method sets bounds to a renderer component and invokes the following internal method:
    //   Component.notifyNewBounds
    // All default and simple renderers do not post specified events,
    // but panel-based renderers have to post events by contract.
    switch (event.getID()) {
      case ComponentEvent.COMPONENT_MOVED:
      case ComponentEvent.COMPONENT_RESIZED:
      case HierarchyEvent.ANCESTOR_MOVED:
      case HierarchyEvent.ANCESTOR_RESIZED:
        Object source = event.getSource();
        if (source instanceof Component &&
            ComponentUtil.getParentOfType((Class<? extends CellRendererPane>)CellRendererPane.class, (Component)source) != null) {
          return true;
        }
    }
    return false;
  }

  private void abracadabraDaberBoreh() {
    // We need to track if there are KeyBoardEvents in IdeEventQueue
    // So we want to intercept all events posted to IdeEventQueue and increment counters
    // However, the regular control flow goes like this:
    //    PostEventQueue.flush() -> EventQueue.postEvent() -> IdeEventQueue.postEventPrivate() -> AAAA we missed event, because postEventPrivate() can't be overridden.
    // Instead, we do following:
    //  - create new PostEventQueue holding our IdeEventQueue instead of old EventQueue
    //  - replace "PostEventQueue" value in AppContext with this new PostEventQueue
    // After that the control flow goes like this:
    //    PostEventQueue.flush() -> IdeEventQueue.postEvent() -> We intercepted event, incremented counters.
    try {
      Class<?> aClass = Class.forName("sun.awt.PostEventQueue");
      Constructor<?> constructor = aClass.getDeclaredConstructor(EventQueue.class);
      constructor.setAccessible(true);
      Object postEventQueue = constructor.newInstance(this);
      AppContext.getAppContext().put("PostEventQueue", postEventQueue);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void setWindowManager(@NotNull WindowManagerEx windowManager) {
    myWindowManager = windowManager;
  }

  public void addIdleListener(@NotNull final Runnable runnable, final int timeoutMillis) {
    if (timeoutMillis <= 0 || TimeUnit.MILLISECONDS.toHours(timeoutMillis) >= 24) {
      throw new IllegalArgumentException("This timeout value is unsupported: " + timeoutMillis);
    }
    synchronized (myLock) {
      myIdleListeners.add(runnable);
      final MyFireIdleRequest request = new MyFireIdleRequest(runnable, timeoutMillis);
      myListenerToRequest.put(runnable, request);
      UIUtil.invokeLaterIfNeeded(() -> myIdleRequestsAlarm.addRequest(request, timeoutMillis));
    }
  }

  public void removeIdleListener(@NotNull final Runnable runnable) {
    synchronized (myLock) {
      final boolean wasRemoved = myIdleListeners.remove(runnable);
      if (!wasRemoved) {
        LOG.error("unknown runnable: " + runnable);
      }
      final MyFireIdleRequest request = myListenerToRequest.remove(runnable);
      LOG.assertTrue(request != null);
      myIdleRequestsAlarm.cancelRequest(request);
    }
  }

  public void addActivityListener(@NotNull Runnable runnable, @NotNull Disposable parentDisposable) {
    ContainerUtil.add(runnable, myActivityListeners, parentDisposable);
  }

  public void addDispatcher(@NotNull EventDispatcher dispatcher, Disposable parent) {
    _addProcessor(dispatcher, parent, myDispatchers);
  }

  public void removeDispatcher(@NotNull EventDispatcher dispatcher) {
    myDispatchers.remove(dispatcher);
  }

  public boolean containsDispatcher(@NotNull EventDispatcher dispatcher) {
    return myDispatchers.contains(dispatcher);
  }

  public void addPostprocessor(@NotNull EventDispatcher dispatcher, @Nullable Disposable parent) {
    _addProcessor(dispatcher, parent, myPostProcessors);
  }

  public void removePostprocessor(@NotNull EventDispatcher dispatcher) {
    myPostProcessors.remove(dispatcher);
  }

  private static void _addProcessor(@NotNull EventDispatcher dispatcher,
                                    Disposable parent,
                                    @NotNull Collection<? super EventDispatcher> set) {
    set.add(dispatcher);
    if (parent != null) {
      Disposer.register(parent, () -> set.remove(dispatcher));
    }
  }

  public int getEventCount() {
    return myEventCount;
  }

  public void setEventCount(int evCount) {
    myEventCount = evCount;
  }

  @NotNull
  public AWTEvent getTrueCurrentEvent() {
    return myCurrentEvent;
  }

  private static boolean ourAppIsLoaded;

  private static boolean appIsLoaded() {
    if (ourAppIsLoaded) {
      return true;
    }

    boolean loaded = ApplicationManagerEx.isAppLoaded();
    if (loaded) {
      ourAppIsLoaded = true;
    }
    return loaded;
  }

  //Use for GuiTests to stop IdeEventQueue when application is disposed already
  public static void applicationClose() {
    ourAppIsLoaded = false;
  }

  private boolean skipTypedEvents;

  @Override
  public void dispatchEvent(@NotNull AWTEvent e) {
    long startedAt = System.currentTimeMillis();
    if (SystemProperties.getBooleanProperty("skip.typed.event", true) && skipTypedKeyEventsIfFocusReturnsToOwner(e)) {
      return;
    }

    if (isMetaKeyPressedOnLinux(e)) return;

    if (e.getSource() instanceof TrayIcon) return;

    checkForTimeJump();

    if (!appIsLoaded()) {
      try {
        super.dispatchEvent(e);
      }
      catch (Throwable t) {
        processException(t);
      }
      return;
    }

    e = mapEvent(e);
    AWTEvent metaEvent = mapMetaState(e);
    if (Registry.is("keymap.windows.as.meta") && metaEvent != null) {
      e = metaEvent;
    }
    if (JAVA11_ON_MAC && e instanceof InputEvent) {
      // disable AltGr on Mac because this key is not supported by macOS
      if (e instanceof KeyEvent && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_ALT_GRAPH) ((KeyEvent)e).setKeyCode(KeyEvent.VK_ALT);
      IdeKeyEventDispatcher.removeAltGraph((InputEvent)e);
    }

    boolean wasInputEvent = myIsInInputEvent;
    myIsInInputEvent = isInputEvent(e);
    AWTEvent oldEvent = myCurrentEvent;
    myCurrentEvent = e;

    try (AccessToken ignored = startActivity(e)) {
      ProgressManager progressManager = obtainProgressManager();
      if (progressManager != null) {
        progressManager.computePrioritized(() -> {
          _dispatchEvent(myCurrentEvent);
          return null;
        });
      } else {
        _dispatchEvent(myCurrentEvent);
      }
    }
    catch (Throwable t) {
      processException(t);
    }
    finally {
      myIsInInputEvent = wasInputEvent;
      myCurrentEvent = oldEvent;

      for (EventDispatcher each : myPostProcessors) {
        each.dispatch(e);
      }

      if (e instanceof KeyEvent) {
        maybeReady();
      }
      TransactionGuardImpl.logTimeMillis(startedAt, e);
    }

    if (isFocusEvent(e)) {
      TouchBarsManager.onFocusEvent(e);

      if (FOCUS_AWARE_RUNNABLES_LOG.isDebugEnabled()) {
        FOCUS_AWARE_RUNNABLES_LOG.debug("Focus event list (execute on focus event): " + runnablesWaitingForFocusChangeState());
      }
      List<AWTEvent> events = new ArrayList<>();
      while (!focusEventsList.isEmpty()) {
        AWTEvent f = focusEventsList.poll();
        events.add(f);
        if (f.equals(e)) break;
      }
      events.stream()
        .map(entry -> myRunnablesWaitingFocusChange.remove(entry))
        .filter(lor -> lor != null)
        .flatMap(listOfRunnables -> listOfRunnables.stream())
        .filter(r -> r != null)
        .filter(r -> !(r instanceof ExpirableRunnable && ((ExpirableRunnable)r).isExpired()))
        .forEach(runnable -> {
          try {
            runnable.run();
          }
          catch (Exception ex) {
            LOG.error(ex);
          }
        });
    }
  }

  @Nullable
  private static ProgressManager obtainProgressManager() {
    ProgressManager manager = ourProgressManager;
    if (manager == null) {
      Application app = ApplicationManager.getApplication();
      if (app != null && !app.isDisposed()) {
        ourProgressManager = manager = ServiceManager.getService(ProgressManager.class);
      }
    }
    return manager;
  }

  private static boolean isMetaKeyPressedOnLinux(@NotNull AWTEvent e) {
    if (!SystemProperties.getBooleanProperty("keymap.skip.meta.press.on.linux", false)) {
      return false;
    }

    boolean metaIsPressed = e instanceof InputEvent && (((InputEvent)e).getModifiersEx() & InputEvent.META_DOWN_MASK) != 0;
    boolean typedKeyEvent = e.getID() == KeyEvent.KEY_TYPED;
    return SystemInfo.isLinux && typedKeyEvent && metaIsPressed;
  }

  private boolean skipTypedKeyEventsIfFocusReturnsToOwner(@NotNull AWTEvent e) {
    if (e.getID() == WindowEvent.WINDOW_LOST_FOCUS) {
      WindowEvent wfe = (WindowEvent)e;
      if (wfe.getWindow().getParent() != null && wfe.getWindow().getParent() == wfe.getOppositeWindow()) {
        skipTypedEvents = true;
      }
    }

    if (skipTypedEvents && e instanceof KeyEvent) {
      if (e.getID() == KeyEvent.KEY_TYPED) {
        ((KeyEvent)e).consume();
        return true;
      }
      else {
        skipTypedEvents = false;
      }
    }

    return false;
  }

  //As we rely on system time monotonicity in many places let's log anomalies at least.
  private void checkForTimeJump() {
    long now = System.currentTimeMillis();
    if (myLastEventTime > now + 1000) {
      LOG.warn("System clock's jumped back by ~" + (myLastEventTime - now) / 1000 + " sec");
    }
    myLastEventTime = now;
  }

  private static boolean isInputEvent(@NotNull AWTEvent e) {
    return e instanceof InputEvent || e instanceof InputMethodEvent || e instanceof WindowEvent || e instanceof ActionEvent;
  }

  @Override
  @NotNull
  public AWTEvent getNextEvent() throws InterruptedException {
    AWTEvent event = super.getNextEvent();
    if (isKeyboardEvent(event) && myKeyboardEventsDispatched.incrementAndGet() > myKeyboardEventsPosted.get()) {
      throw new RuntimeException(event + "; posted: " + myKeyboardEventsPosted + "; dispatched: " + myKeyboardEventsDispatched);
    }
    return event;
  }

  @Nullable
  static AccessToken startActivity(@NotNull AWTEvent e) {
    if (ourTransactionGuard == null && appIsLoaded()) {
      if (ApplicationManager.getApplication() != null && !ApplicationManager.getApplication().isDisposed()) {
        ourTransactionGuard = (TransactionGuardImpl)TransactionGuard.getInstance();
      }
    }
    return ourTransactionGuard == null
           ? null
           : ourTransactionGuard.startActivity(isInputEvent(e) || e instanceof ItemEvent || e instanceof FocusEvent);
  }

  private void processException(@NotNull Throwable t) {
    if (!myToolkitBugsProcessor.process(t)) {
      MainRunner.processException(t);
    }
  }

  @NotNull
  private static AWTEvent mapEvent(@NotNull AWTEvent e) {
    if (SystemInfo.isXWindow && e instanceof MouseEvent && ((MouseEvent)e).getButton() > 3) {
      MouseEvent src = (MouseEvent)e;
      if (src.getButton() < 6) {
        // Convert these events(buttons 4&5 in are produced by touchpad, they must be converted to horizontal scrolling events
        e = new MouseWheelEvent(src.getComponent(), MouseEvent.MOUSE_WHEEL, src.getWhen(),
                                src.getModifiers() | InputEvent.SHIFT_DOWN_MASK, src.getX(), src.getY(),
                                0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, src.getClickCount(), src.getButton() == 4 ? -1 : 1);
      }
      else {
        // Here we "shift" events with buttons 6 and 7 to similar events with buttons 4 and 5
        // See java.awt.InputEvent#BUTTON_DOWN_MASK, 1<<14 is 4th physical button, 1<<15 is 5th.
        //noinspection MagicConstant
        e = new MouseEvent(src.getComponent(), src.getID(), src.getWhen(), src.getModifiers() | (1 << 8 + src.getButton()),
                           src.getX(), src.getY(), 1, src.isPopupTrigger(), src.getButton() - 2);
      }
    }
    return e;
  }

  /**
   * Here we try to use 'Windows' key like modifier, so we patch events with modifier 'Meta'
   * when 'Windows' key was pressed and still is not released.
   *
   * @param e event to be patched
   * @return new 'patched' event if need, otherwise null
   * <p>
   * Note: As side-effect this method tracks special flag for 'Windows' key state that is valuable on itself
   */
  @Nullable
  private AWTEvent mapMetaState(@NotNull AWTEvent e) {
    if (myWinMetaPressed) {
      Application app = ApplicationManager.getApplication();

      boolean weAreNotActive = app == null || !app.isActive();
      weAreNotActive |= e instanceof FocusEvent && ((FocusEvent)e).getOppositeComponent() == null;
      if (weAreNotActive) {
        myWinMetaPressed = false;
        return null;
      }
    }

    if (e instanceof KeyEvent) {
      KeyEvent ke = (KeyEvent)e;
      if (ke.getKeyCode() == KeyEvent.VK_WINDOWS) {
        if (ke.getID() == KeyEvent.KEY_PRESSED) myWinMetaPressed = true;
        if (ke.getID() == KeyEvent.KEY_RELEASED) myWinMetaPressed = false;
        return null;
      }
      if (myWinMetaPressed) {
        return new KeyEvent(ke.getComponent(), ke.getID(), ke.getWhen(), ke.getModifiers() | ke.getModifiersEx() | InputEvent.META_MASK,
                            ke.getKeyCode(),
                            ke.getKeyChar(), ke.getKeyLocation());
      }
    }

    if (myWinMetaPressed && e instanceof MouseEvent && ((MouseEvent)e).getButton() != 0) {
      MouseEvent me = (MouseEvent)e;
      return new MouseEvent(me.getComponent(), me.getID(), me.getWhen(), me.getModifiers() | me.getModifiersEx() | InputEvent.META_MASK,
                            me.getX(), me.getY(),
                            me.getClickCount(), me.isPopupTrigger(), me.getButton());
    }

    return null;
  }

  private void _dispatchEvent(@NotNull AWTEvent e) {
    if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
      DnDManagerImpl dndManager = (DnDManagerImpl)DnDManager.getInstance();
      if (dndManager != null) {
        dndManager.setLastDropHandler(null);
      }
    }

    myEventCount++;

    if (processAppActivationEvents(e)) return;

    myKeyboardBusy = e instanceof KeyEvent || myKeyboardEventsPosted.get() > myKeyboardEventsDispatched.get();

    if (e instanceof KeyEvent) {
      if (e.getID() == KeyEvent.KEY_RELEASED && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_SHIFT) {
        myMouseEventDispatcher.resetHorScrollingTracker();
      }
    }

    if (e instanceof WindowEvent || e instanceof FocusEvent) {
      ActivityTracker.getInstance().inc();
    }

    if (e instanceof MouseWheelEvent) {
      final MenuElement[] selectedPath = MenuSelectionManager.defaultManager().getSelectedPath();
      if (selectedPath.length > 0 && !(selectedPath[0] instanceof ComboPopup)) {
        ((MouseWheelEvent)e).consume();
        Component component = selectedPath[0].getComponent();
        if (component instanceof JBPopupMenu) {
          ((JBPopupMenu)component).processMouseWheelEvent((MouseWheelEvent)e);
        }
        return;
      }
    }


    // Process "idle" and "activity" listeners
    if (e instanceof KeyEvent || e instanceof MouseEvent) {
      ActivityTracker.getInstance().inc();

      synchronized (myLock) {
        myIdleRequestsAlarm.cancelAllRequests();
        for (Runnable idleListener : myIdleListeners) {
          final MyFireIdleRequest request = myListenerToRequest.get(idleListener);
          if (request == null) {
            LOG.error("There is no request for " + idleListener);
          }
          else {
            myIdleRequestsAlarm.addRequest(request, request.getTimeout(), ModalityState.NON_MODAL);
          }
        }
        if (KeyEvent.KEY_PRESSED == e.getID() ||
            KeyEvent.KEY_TYPED == e.getID() ||
            MouseEvent.MOUSE_PRESSED == e.getID() ||
            MouseEvent.MOUSE_RELEASED == e.getID() ||
            MouseEvent.MOUSE_CLICKED == e.getID()) {
          myLastActiveTime = System.nanoTime();
          for (Runnable activityListener : myActivityListeners) {
            activityListener.run();
          }
        }
      }
    }

    // We must ignore typed events that are dispatched between KEY_PRESSED and KEY_RELEASED.
    // Key event dispatcher resets its state on KEY_RELEASED event
    if (e.getID() == KeyEvent.KEY_TYPED &&
        myKeyEventDispatcher.isPressedWasProcessed()) {
      ((KeyEvent)e).consume();
    }

    if (myPopupManager.isPopupActive() && myPopupManager.dispatch(e)) {
      if (myKeyEventDispatcher.isWaitingForSecondKeyStroke()) {
        myKeyEventDispatcher.setState(KeyState.STATE_INIT);
      }

      return;
    }

    if (e instanceof InputEvent && SystemInfo.isMac) {
      TouchBarsManager.onInputEvent((InputEvent)e);
    }

    if (dispatchByCustomDispatchers(e)) {
      return;
    }

    if (e instanceof InputMethodEvent) {
      if (SystemInfo.isMac && myKeyEventDispatcher.isWaitingForSecondKeyStroke()) {
        return;
      }
    }

    if (e instanceof ComponentEvent && myWindowManager != null) {
      myWindowManager.dispatchComponentEvent((ComponentEvent)e);
    }

    if (e instanceof KeyEvent) {
      if (
        !SystemInfo.isJetBrainsJvm ||
        (JavaVersion.current().compareTo(JavaVersion.compose(8, 0, 202, 1504, false)) < 0 &&
         JavaVersion.current().compareTo(JavaVersion.compose(9, 0, 0, 0, false)) < 0) ||
        JavaVersion.current().compareTo(JavaVersion.compose(11, 0, 0, 0, false)) > 0
      ) {
        if (myKeyEventDispatcher.dispatchKeyEvent((KeyEvent)e)) {
          ((KeyEvent)e).consume();
        }
      }
      defaultDispatchEvent(e);
    }
    else if (e instanceof MouseEvent) {
      MouseEvent me = (MouseEvent)e;
      if (me.getID() == MouseEvent.MOUSE_PRESSED && me.getModifiers() > 0 && me.getModifiersEx() == 0) {
        // In case of these modifiers java.awt.Container#LightweightDispatcher.processMouseEvent() uses a recent 'active' component
        // from inner WeakReference (see mouseEventTarget field) even if the component has been already removed from component hierarchy.
        // So we have to reset this WeakReference with synthetic event just before processing of actual event
        super.dispatchEvent(new MouseEvent(me.getComponent(), MouseEvent.MOUSE_MOVED, me.getWhen(), 0, me.getX(), me.getY(), 0, false, 0));
      }
      if (IdeMouseEventDispatcher.patchClickCount(me) && me.getID() == MouseEvent.MOUSE_CLICKED) {
        final MouseEvent toDispatch =
          new MouseEvent(me.getComponent(), me.getID(), System.currentTimeMillis(), me.getModifiers(), me.getX(), me.getY(), 1,
                         me.isPopupTrigger(), me.getButton());
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> dispatchEvent(toDispatch));
      }
      if (!myMouseEventDispatcher.dispatchMouseEvent(me)) {
        defaultDispatchEvent(e);
      }
    }
    else {
      defaultDispatchEvent(e);
    }
  }

  private boolean dispatchByCustomDispatchers(@NotNull AWTEvent e) {
    for (EventDispatcher eachDispatcher : myDispatchers) {
      if (eachDispatcher.dispatch(e)) {
        return true;
      }
    }
    return false;
  }

  private static boolean processAppActivationEvents(@NotNull AWTEvent e) {
    if (e instanceof WindowEvent) {
      final WindowEvent we = (WindowEvent)e;

      ApplicationActivationStateManager.updateState(we);

      storeLastFocusedComponent(we);
    }

    return false;
  }

  private static void storeLastFocusedComponent(@NotNull WindowEvent we) {
    final Window eventWindow = we.getWindow();

    if (we.getID() == WindowEvent.WINDOW_DEACTIVATED || we.getID() == WindowEvent.WINDOW_LOST_FOCUS) {
      Component frame = ComponentUtil.findUltimateParent(eventWindow);
      Component focusOwnerInDeactivatedWindow = eventWindow.getMostRecentFocusOwner();
      IdeFrame[] allProjectFrames = WindowManager.getInstance().getAllProjectFrames();

      if (focusOwnerInDeactivatedWindow != null) {
        for (IdeFrame ideFrame : allProjectFrames) {
          JFrame aFrame = WindowManager.getInstance().getFrame(ideFrame.getProject());
          if (aFrame.equals(frame)) {
            IdeFocusManager focusManager = IdeFocusManager.getGlobalInstance();
            if (focusManager instanceof FocusManagerImpl) {
              ((FocusManagerImpl)focusManager).setLastFocusedAtDeactivation(ideFrame, focusOwnerInDeactivatedWindow);
            }
          }
        }
      }
    }
  }

  private void defaultDispatchEvent(@NotNull AWTEvent e) {
    try {
      maybeReady();
      fixStickyAlt(e);
      super.dispatchEvent(e);
    }
    catch (Throwable t) {
      processException(t);
    }
  }

  private static void fixStickyAlt(@NotNull AWTEvent e) {
    if (!Registry.is("actionSystem.win.suppressAlt.new") &&
        SystemInfo.isWinXpOrNewer &&
        !SystemInfo.isWinVistaOrNewer &&
        e instanceof KeyEvent &&
        ((KeyEvent)e).getKeyCode() == KeyEvent.VK_ALT) {
      ((KeyEvent)e).consume();  // IDEA-17359
    }
  }

  public void flushQueue() {
    while (true) {
      AWTEvent event = peekEvent();
      if (event == null) return;
      try {
        AWTEvent event1 = getNextEvent();
        dispatchEvent(event1);
      }
      catch (Exception e) {
        LOG.error(e); //?
      }
    }
  }

  public void pumpEventsForHierarchy(@NotNull Component modalComponent, @NotNull Condition<? super AWTEvent> exitCondition) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("pumpEventsForHierarchy(" + modalComponent + ", " + exitCondition + ")");
    }
    AWTEvent event;
    do {
      try {
        event = getNextEvent();
        boolean eventOk = true;
        if (event instanceof InputEvent) {
          final Object s = event.getSource();
          if (s instanceof Component) {
            Component c = (Component)s;
            Window modalWindow = SwingUtilities.windowForComponent(modalComponent);
            while (c != null && c != modalWindow) c = c.getParent();
            if (c == null) {
              eventOk = false;
              if (LOG.isDebugEnabled()) {
                LOG.debug("pumpEventsForHierarchy.consumed: " + event);
              }
              ((InputEvent)event).consume();
            }
          }
        }

        if (eventOk) {
          dispatchEvent(event);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
        event = null;
      }
    }
    while (!exitCondition.value(event));
    if (LOG.isDebugEnabled()) {
      LOG.debug("pumpEventsForHierarchy.exit(" + modalComponent + ", " + exitCondition + ")");
    }
  }

  @FunctionalInterface
  public interface EventDispatcher {
    boolean dispatch(@NotNull AWTEvent e);
  }

  private final class MyFireIdleRequest implements Runnable {
    private final Runnable myRunnable;
    private final int myTimeout;


    MyFireIdleRequest(@NotNull Runnable runnable, final int timeout) {
      myTimeout = timeout;
      myRunnable = runnable;
    }


    @Override
    public void run() {
      myRunnable.run();
      synchronized (myLock) {
        if (myIdleListeners.contains(myRunnable)) // do not reschedule if not interested anymore
        {
          myIdleRequestsAlarm.addRequest(this, myTimeout, ModalityState.NON_MODAL);
        }
      }
    }

    public int getTimeout() {
      return myTimeout;
    }

    @Override
    public String toString() {
      return "Fire idle request. delay: " + getTimeout() + "; runnable: " + myRunnable;
    }
  }

  public long getIdleTime() {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - myLastActiveTime);
  }

  @NotNull
  public IdePopupManager getPopupManager() {
    return myPopupManager;
  }

  @NotNull
  public IdeKeyEventDispatcher getKeyEventDispatcher() {
    return myKeyEventDispatcher;
  }

  /**
   * Same as {@link #blockNextEvents(MouseEvent, IdeEventQueue.BlockMode)} with {@code blockMode} equal to {@code COMPLETE}.
   */
  public void blockNextEvents(@NotNull MouseEvent e) {
    blockNextEvents(e, BlockMode.COMPLETE);
  }

  /**
   * When {@code blockMode} is {@code COMPLETE}, blocks following related mouse events completely, when {@code blockMode} is
   * {@code ACTIONS} only blocks performing actions bound to corresponding mouse shortcuts.
   */
  public void blockNextEvents(@NotNull MouseEvent e, @NotNull BlockMode blockMode) {
    myMouseEventDispatcher.blockNextEvents(e, blockMode);
  }

  private boolean isReady() {
    return !myKeyboardBusy && myKeyEventDispatcher.isReady();
  }

  public void maybeReady() {
    if (myReady.isEmpty() || !isReady()) return;

    Runnable[] ready = myReady.toArray(new Runnable[0]);
    myReady.clear();

    for (Runnable each : ready) {
      each.run();
    }
  }

  public void doWhenReady(@NotNull Runnable runnable) {
    if (EventQueue.isDispatchThread()) {
      myReady.add(runnable);
      maybeReady();
    }
    else {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        myReady.add(runnable);
        maybeReady();
      });
    }
  }

  public boolean isPopupActive() {
    return myPopupManager.isPopupActive();
  }

  private static class WindowsAltSuppressor implements EventDispatcher {
    private boolean myWaitingForAltRelease;
    private Robot myRobot;

    @Override
    public boolean dispatch(@NotNull AWTEvent e) {
      boolean dispatch = true;
      if (e instanceof KeyEvent) {
        KeyEvent ke = (KeyEvent)e;
        final Component component = ke.getComponent();
        boolean pureAlt = ke.getKeyCode() == KeyEvent.VK_ALT && (ke.getModifiers() | InputEvent.ALT_MASK) == InputEvent.ALT_MASK;
        if (!pureAlt) {
          myWaitingForAltRelease = false;
        }
        else {
          UISettings uiSettings = UISettings.getInstanceOrNull();
          if (uiSettings == null ||
              !SystemInfo.isWindows ||
              !Registry.is("actionSystem.win.suppressAlt") ||
              !(uiSettings.getHideToolStripes() || uiSettings.getPresentationMode())) {
            return false;
          }

          if (ke.getID() == KeyEvent.KEY_PRESSED) {
            dispatch = !myWaitingForAltRelease;
          }
          else if (ke.getID() == KeyEvent.KEY_RELEASED) {
            if (myWaitingForAltRelease) {
              myWaitingForAltRelease = false;
              dispatch = false;
            }
            else if (component != null) {
              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(() -> {
                try {
                  final Window window = ComponentUtil.getWindow(component);
                  if (window == null || !window.isActive()) {
                    return;
                  }
                  myWaitingForAltRelease = true;
                  if (myRobot == null) {
                    myRobot = new Robot();
                  }
                  myRobot.keyPress(KeyEvent.VK_ALT);
                  myRobot.keyRelease(KeyEvent.VK_ALT);
                }
                catch (AWTException e1) {
                  LOG.debug(e1);
                }
              });
            }
          }
        }
      }

      return !dispatch;
    }
  }

  //Windows OS doesn't support a Windows+Up/Down shortcut for dialogs, so we provide a workaround
  private class WindowsUpMaximizer implements EventDispatcher {
    @SuppressWarnings("SSBasedInspection")
    @Override
    public boolean dispatch(@NotNull AWTEvent e) {
      if (myWinMetaPressed
          && e instanceof KeyEvent
          && e.getID() == KeyEvent.KEY_RELEASED
          && (((KeyEvent)e).getKeyCode() == KeyEvent.VK_UP || ((KeyEvent)e).getKeyCode() == KeyEvent.VK_DOWN)) {
        Component parent = ComponentUtil.getWindow(((KeyEvent)e).getComponent());
        if (parent instanceof JDialog) {
          final JDialog dialog = (JDialog)parent;
          SwingUtilities.invokeLater(() -> {
            if (((KeyEvent)e).getKeyCode() == KeyEvent.VK_UP) {
              MaximizeActiveDialogAction.maximize(dialog);
            }
            else {
              MaximizeActiveDialogAction.normalize(dialog);
            }
          });
          return true;
        }
      }
      return false;
    }
  }

  //We have to stop editing with <ESC> (if any) and consume the event to prevent any further processing (dialog closing etc.)
  private static class EditingCanceller implements EventDispatcher {
    @Override
    public boolean dispatch(@NotNull AWTEvent e) {
      if (e instanceof KeyEvent && e.getID() == KeyEvent.KEY_PRESSED && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_ESCAPE &&
          !getInstance().getPopupManager().isPopupActive()) {
        final Component owner = ComponentUtil.findParentByCondition(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(),
                                                             component -> component instanceof JTable || component instanceof JTree);

        if (owner instanceof JTable && ((JTable)owner).isEditing()) {
          ((JTable)owner).editingCanceled(null);
          return true;
        }
        if (owner instanceof JTree && ((JTree)owner).isEditing()) {
          ((JTree)owner).cancelEditing();
          return true;
        }
      }
      return false;
    }
  }

  public boolean isInputMethodEnabled() {
    return !SystemInfo.isMac || myInputMethodLock == 0;
  }

  public void disableInputMethods(@NotNull Disposable parentDisposable) {
    myInputMethodLock++;
    Disposer.register(parentDisposable, () -> myInputMethodLock--);
  }

  private final FrequentEventDetector myFrequentEventDetector = new FrequentEventDetector(1009, 100);

  @Override
  public void postEvent(@NotNull AWTEvent event) {
    doPostEvent(event);
  }

  /**
   * Checks if focus is being transferred from IDE frame to a heavyweight popup.
   * For this, we use {@link WindowEvent}s that notify us about opened or focused windows.
   * We assume that by this moment AWT has enabled its typeahead machinery, so
   * after this check, it is safe to dequeue all postponed key events
   */
  private static boolean doesFocusGoIntoPopup(@NotNull AWTEvent e) {
    AWTEvent unwrappedEvent = unwrapWindowEvent(e);

    if (TYPEAHEAD_LOG.isDebugEnabled() && (e instanceof WindowEvent || e.getClass().getName().contains("SequencedEvent"))) {
      TYPEAHEAD_LOG.debug("Window event: " + e.paramString());
    }

    return doesFocusGoIntoPopupFromWindowEvent(unwrappedEvent);
  }

  private static class SequencedEventNestedFieldHolder {
    private static final Field NESTED_FIELD;
    private static final Class<?> SEQUENCED_EVENT_CLASS;

    static {
      try {
        SEQUENCED_EVENT_CLASS = Class.forName("java.awt.SequencedEvent");
        NESTED_FIELD = ReflectionUtil.getDeclaredField(SEQUENCED_EVENT_CLASS, "nested");
        if (NESTED_FIELD == null) throw new RuntimeException();
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @NotNull
  private static AWTEvent unwrapWindowEvent(@NotNull AWTEvent e) {
    AWTEvent unwrappedEvent = e;
    if (e.getClass() == SequencedEventNestedFieldHolder.SEQUENCED_EVENT_CLASS) {
      try {
        unwrappedEvent = (AWTEvent)SequencedEventNestedFieldHolder.NESTED_FIELD.get(e);
      }
      catch (IllegalAccessException illegalAccessException) {
        TYPEAHEAD_LOG.error(illegalAccessException);
      }
    }
    TYPEAHEAD_LOG.assertTrue(unwrappedEvent != null);
    return unwrappedEvent;
  }

  private boolean isTypeaheadTimeoutExceeded() {
    if (!delayKeyEvents.get()) return false;
    long currentTypeaheadDelay = System.currentTimeMillis() - lastTypeaheadTimestamp;
    if (currentTypeaheadDelay > Registry.get("action.aware.typeaheadTimout").asDouble()) {
      // Log4j uses appenders. The appenders potentially may use invokeLater method
      // In this particular place it is possible to get a deadlock because of
      // sun.awt.PostEventQueue#flush implementation.
      // This is why we need to log the message on the event dispatch thread
      super.postEvent(new InvocationEvent(this, () ->
        TYPEAHEAD_LOG.error(new RuntimeException("Typeahead timeout is exceeded: " + currentTypeaheadDelay))
      ));
      return true;
    }
    return false;
  }

  private static boolean doesFocusGoIntoPopupFromWindowEvent(@NotNull AWTEvent e) {
    if (e.getID() == WindowEvent.WINDOW_GAINED_FOCUS ||
        SystemInfo.isLinux && e.getID() == WindowEvent.WINDOW_OPENED) {
      if (UIUtil.isTypeAheadAware(((WindowEvent)e).getWindow())) {
        TYPEAHEAD_LOG.debug("Focus goes into TypeAhead aware window");
        return true;
      }
    }
    return false;
  }

  private static boolean isFocusEvent(@NotNull AWTEvent e) {
    return
      e.getID() == FocusEvent.FOCUS_GAINED ||
      e.getID() == FocusEvent.FOCUS_LOST ||
      e.getID() == WindowEvent.WINDOW_ACTIVATED ||
      e.getID() == WindowEvent.WINDOW_DEACTIVATED ||
      e.getID() == WindowEvent.WINDOW_LOST_FOCUS ||
      e.getID() == WindowEvent.WINDOW_GAINED_FOCUS;
  }

  private final Queue<AWTEvent> focusEventsList = new ConcurrentLinkedQueue<>();
  private final AtomicLong ourLastTimePressed = new AtomicLong(0);

  // return true if posted, false if consumed immediately
  boolean doPostEvent(@NotNull AWTEvent event) {
    for (PostEventHook listener : myPostEventListeners.getListeners()) {
      if (listener.consumePostedEvent(event)) return false;
    }

    String message = myFrequentEventDetector.getMessageOnEvent(event);
    if (message != null) {
      // we can't log right here, because logging has locks inside, and postEvents can deadlock if it's blocked by anything (IDEA-161322)
      NonUrgentExecutor.getInstance().execute(() -> myFrequentEventDetector.logMessage(message));
    }

    boolean typeAheadEnabled = SystemProperties.getBooleanProperty("action.aware.typeAhead", true);
    if (isKeyboardEvent(event)) {
      myKeyboardEventsPosted.incrementAndGet();
      if (typeAheadEnabled && delayKeyEvents.get()) {
        myDelayedKeyEvents.offer((KeyEvent)event);
        if (TYPEAHEAD_LOG.isDebugEnabled()) {
          TYPEAHEAD_LOG.debug("Waiting for typeahead : " + event);
        }
        return true;
      }
    }

    if (isFocusEvent(event)) {
      focusEventsList.add(event);
    }

    boolean typeAheadSearchEverywhereEnabled = SystemProperties.getBooleanProperty("action.aware.typeAhead.searchEverywhere", false);
    if (typeAheadEnabled) {
      if (event.getID() == KeyEvent.KEY_PRESSED) {
        KeyEvent keyEvent = (KeyEvent)event;
        KeyStroke keyStrokeToFind = KeyStroke.getKeyStroke(keyEvent.getKeyCode(), keyEvent.getModifiers());
        boolean thisShortcutMayShowPopup = ContainerUtil.exists(getShortcutsShowingPopups(),
          s -> s instanceof KeyboardShortcut
            && ((KeyboardShortcut)s).getSecondKeyStroke() == null
            && ((KeyboardShortcut)s).getFirstKeyStroke().equals(keyStrokeToFind));

        if (!isActionPopupShown() && thisShortcutMayShowPopup && KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() instanceof IdeFrame) {
          if (TYPEAHEAD_LOG.isDebugEnabled()) {
            TYPEAHEAD_LOG.debug("Delay following events; Focused window is " +
                                KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow().getClass().getName());
          }
          delayKeyEvents.set(true);
          lastTypeaheadTimestamp = System.currentTimeMillis();
        }
      }
      else if (event.getID() == KeyEvent.KEY_RELEASED && typeAheadSearchEverywhereEnabled
               && KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() instanceof IdeFrame) {
        KeyEvent keyEvent = (KeyEvent)event;
        // 1. check key code
        // 2. if key code != SHIFT -> restart
        // 3. if has other modifiers - > restart
        // 4. keyEvent.getWhen() - ourLastTimePressed.get() < 100 -> restart
        // 5. if the second time and (keyEvent.getWhen() - ourLastTimePressed.get() > 500) -> restart state
        if (keyEvent.getKeyCode() == KeyEvent.VK_SHIFT) {
          switch (mySearchEverywhereTypeaheadState) {
            case DEACTIVATED:
              mySearchEverywhereTypeaheadState = SearchEverywhereTypeaheadState.TRIGGERED;
              ourLastTimePressed.set(keyEvent.getWhen());
              break;
            case TRIGGERED:
              long timeDelta = keyEvent.getWhen() - ourLastTimePressed.get();
              if (!isActionPopupShown() && timeDelta >= 100 && timeDelta <= 500) {
                delayKeyEvents.set(true);
                lastTypeaheadTimestamp = System.currentTimeMillis();
                mySearchEverywhereTypeaheadState = SearchEverywhereTypeaheadState.DETECTED;
              }
              else {
                mySearchEverywhereTypeaheadState = SearchEverywhereTypeaheadState.DEACTIVATED;
                flushDelayedKeyEvents();
                // no need to reset ourLastTimePressed
              }
              break;
            case DETECTED:
              break;
          }
        }
      }

      if (isTypeaheadTimeoutExceeded()) {
        TYPEAHEAD_LOG.debug("Clear delayed events because of IdeFrame deactivation");
        delayKeyEvents.set(false);
        flushDelayedKeyEvents();
        lastTypeaheadTimestamp = 0;
        if (typeAheadSearchEverywhereEnabled) {
          mySearchEverywhereTypeaheadState = SearchEverywhereTypeaheadState.DEACTIVATED;
        }
      }
    }

    super.postEvent(event);

    if (typeAheadSearchEverywhereEnabled &&
        event instanceof KeyEvent &&
        (mySearchEverywhereTypeaheadState == SearchEverywhereTypeaheadState.TRIGGERED ||
         mySearchEverywhereTypeaheadState == SearchEverywhereTypeaheadState.DETECTED)) {
      long timeDelta = ((KeyEvent)event).getWhen() - ourLastTimePressed.get();
      if (timeDelta < 100 || timeDelta > 500) {
        mySearchEverywhereTypeaheadState = SearchEverywhereTypeaheadState.DEACTIVATED;
        flushDelayedKeyEvents();
      }
    }

    if (typeAheadEnabled && doesFocusGoIntoPopup(event)) {
      delayKeyEvents.set(false);
      postDelayedKeyEvents();
      if (typeAheadSearchEverywhereEnabled) {
        mySearchEverywhereTypeaheadState = SearchEverywhereTypeaheadState.DEACTIVATED;
      }
    }

    return true;
  }

  private int numberOfDelayedKeyEvents() {
    // for debug purposes only since it's slow and unreliable
    return myDelayedKeyEvents.size();
  }

  private void postDelayedKeyEvents() {
    if (TYPEAHEAD_LOG.isDebugEnabled()) {
      TYPEAHEAD_LOG.debug("Stop delaying events. Events to post: " + numberOfDelayedKeyEvents());
    }
    KeyEvent event;
    while ((event = myDelayedKeyEvents.poll()) != null) {
      if (TYPEAHEAD_LOG.isDebugEnabled()) {
        TYPEAHEAD_LOG.debug("Posted after delay: " + event.paramString());
      }
      super.postEvent(event);
    }
    if (TYPEAHEAD_LOG.isDebugEnabled()) {
      TYPEAHEAD_LOG.debug("Events after posting: " + numberOfDelayedKeyEvents());
    }
  }

  public void flushDelayedKeyEvents() {
    long startedAt = System.currentTimeMillis();
    if (!isActionPopupShown() && delayKeyEvents.compareAndSet(true, false)) {
      postDelayedKeyEvents();
    }
    TransactionGuardImpl.logTimeMillis(startedAt, "IdeEventQueue#flushDelayedKeyEvents");
  }

  private static boolean isActionPopupShown() {
    if (ApplicationManager.getApplication() == null) {
      return false;
    }

    ActionManager actionManager = ActionManager.getInstance();
    return actionManager instanceof ActionManagerImpl &&
           !((ActionManagerImpl)actionManager).isActionPopupStackEmpty() &&
           !((ActionManagerImpl)actionManager).isToolWindowContextMenuVisible();
  }

  private SearchEverywhereTypeaheadState mySearchEverywhereTypeaheadState = SearchEverywhereTypeaheadState.DEACTIVATED;

  private enum SearchEverywhereTypeaheadState {
    DEACTIVATED,
    TRIGGERED,
    DETECTED
  }

  private final Set<Shortcut> shortcutsShowingPopups = new HashSet<>();
  private WeakReference<Keymap> lastActiveKeymap = new WeakReference<>(null);

  private final List<String> actionsShowingPopupsList = new ArrayList<>();
  private long lastTypeaheadTimestamp = -1;

  @NotNull
  private Set<Shortcut> getShortcutsShowingPopups () {
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager != null) {
      Keymap keymap = keymapManager.getActiveKeymap();
      if (keymap != null) {
        if (!keymap.equals(lastActiveKeymap.get())) {
          String actionsAwareTypeaheadActionsList = Registry.get("action.aware.typeahead.actions.list").asString();
          shortcutsShowingPopups.clear();
          actionsShowingPopupsList.addAll(StringUtil.split(actionsAwareTypeaheadActionsList, ","));
          actionsShowingPopupsList.forEach(actionId -> {
            List<Shortcut> shortcuts = Arrays.asList(keymap.getShortcuts(actionId));
            if (TYPEAHEAD_LOG.isDebugEnabled()) {
              shortcuts.forEach(s -> TYPEAHEAD_LOG.debug("Typeahead for " + actionId + " : Shortcuts: " + s));
            }
            shortcutsShowingPopups.addAll(shortcuts);
          });
          lastActiveKeymap = new WeakReference<>(keymap);
        }
      }
    }
    return shortcutsShowingPopups;
  }

  private final Queue<KeyEvent> myDelayedKeyEvents = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean delayKeyEvents = new AtomicBoolean();

  private static boolean isKeyboardEvent(@NotNull AWTEvent event) {
    return event instanceof KeyEvent;
  }

  @Override
  public AWTEvent peekEvent() {
    AWTEvent event = super.peekEvent();
    if (event != null) {
      return event;
    }
    if (isTestMode() && LaterInvocator.ensureFlushRequested()) {
      return super.peekEvent();
    }
    return null;
  }

  private Boolean myTestMode;
  private boolean isTestMode() {
    Boolean testMode = myTestMode;
    if (testMode != null) return testMode;

    Application application = ApplicationManager.getApplication();
    if (application == null) return false;

    testMode = application.isUnitTestMode();
    myTestMode = testMode;
    return testMode;
  }

  /**
   * @see IdeEventQueue#blockNextEvents(MouseEvent, IdeEventQueue.BlockMode)
   */
  public enum BlockMode {
    COMPLETE, ACTIONS
  }

  /**
   * An absolutely guru API, please avoid using it at all cost.
   */
  @FunctionalInterface
  public interface PostEventHook extends EventListener {
    /**
     * @return true if event is handled by the listener and should't be added to event queue at all
     */
    boolean consumePostedEvent(@NotNull AWTEvent event);
  }

  public void addPostEventListener(@NotNull PostEventHook listener, @NotNull Disposable parentDisposable) {
    myPostEventListeners.addListener(listener, parentDisposable);
  }

  private static class Holder {
    // JBSDK only
    private static final Method unsafeNonBlockingExecuteRef = ReflectionUtil.getDeclaredMethod(SunToolkit.class, "unsafeNonblockingExecute", Runnable.class);
  }

  /**
   * Must be called on the Event Dispatching thread.
   * Executes the runnable so that it can perform a non-blocking invocation on the toolkit thread.
   * Not for general-purpose usage.
   *
   * @param r the runnable to execute
   */
  public static void unsafeNonblockingExecute(@NotNull Runnable r) {
    assert EventQueue.isDispatchThread();
    // The method is available in JBSDK.
    if (Holder.unsafeNonBlockingExecuteRef != null) {
      try {
        Holder.unsafeNonBlockingExecuteRef.invoke(Toolkit.getDefaultToolkit(), r);
        return;
      }
      catch (Exception ignore) {
      }
    }
    r.run();
  }
}
