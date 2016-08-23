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
package com.intellij.ide;

import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDManagerImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.idea.IdeaApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.FrequentEventDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeyboardSettingsExternalizable;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher;
import com.intellij.openapi.keymap.impl.KeyState;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ExpirableRunnable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.FocusManagerImpl;
import com.intellij.util.Alarm;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AppContext;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Vladimir Kondratyev
 * @author Anton Katilin
 */
public class IdeEventQueue extends EventQueue {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.IdeEventQueue");
  private static TransactionGuardImpl ourTransactionGuard;

  /**
   * Adding/Removing of "idle" listeners should be thread safe.
   */
  private final Object myLock = new Object();

  private final List<Runnable> myIdleListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<Runnable> myActivityListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Alarm myIdleRequestsAlarm = new Alarm();
  private final Alarm myIdleTimeCounterAlarm = new Alarm();
  private long myIdleTime;
  private final Map<Runnable, MyFireIdleRequest> myListener2Request = new HashMap<>();
  // IdleListener -> MyFireIdleRequest
  private final IdeKeyEventDispatcher myKeyEventDispatcher = new IdeKeyEventDispatcher(this);
  private final IdeMouseEventDispatcher myMouseEventDispatcher = new IdeMouseEventDispatcher();
  private final IdePopupManager myPopupManager = new IdePopupManager();
  private final ToolkitBugsProcessor myToolkitBugsProcessor = new ToolkitBugsProcessor();
  private boolean mySuspendMode;
  /**
   * We exit from suspend mode when focus owner changes and no more WindowEvent.WINDOW_OPENED events
   * <p/>
   * in the queue. If WINDOW_OPENED event does exists in the queues then we restart the alarm.
   */
  private Component myFocusOwner;
  private final Runnable myExitSuspendModeRunnable = new ExitSuspendModeRunnable();

  /**
   * We exit from suspend mode when this alarm is triggered and no mode WindowEvent.WINDOW_OPENED
   * <p/>
   * events in the queue. If WINDOW_OPENED event does exist then we restart the alarm.
   */
  private final Alarm mySuspendModeAlarm = new Alarm();

  /**
   * Counter of processed events. It is used to assert that data context lives only inside single
   * <p/>
   * Swing event.
   */
  private int myEventCount;
  final AtomicInteger myKeyboardEventsPosted = new AtomicInteger();
  final AtomicInteger myKeyboardEventsDispatched = new AtomicInteger();
  private boolean myIsInInputEvent;
  private AWTEvent myCurrentEvent;
  private long myLastActiveTime;
  private WindowManagerEx myWindowManager;
  private final Set<EventDispatcher> myDispatchers = new LinkedHashSet<>();
  private final Set<EventDispatcher> myPostProcessors = new LinkedHashSet<>();
  private final Set<Runnable> myReady = ContainerUtil.newHashSet();
  private boolean myKeyboardBusy;
  private boolean myDispatchingFocusEvent;
  private boolean myWinMetaPressed;
  private int myInputMethodLock;

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
    addIdleTimeCounterRequest();

    final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    keyboardFocusManager.addPropertyChangeListener("permanentFocusOwner", e -> {
      final Application application = ApplicationManager.getApplication();
      if (application == null) {
        // We can get focus event before application is initialized
        return;
      }
      application.assertIsDispatchThread();
      final Window focusedWindow = keyboardFocusManager.getFocusedWindow();
      final Component focusOwner = keyboardFocusManager.getFocusOwner();
      if (mySuspendMode && focusedWindow != null && focusOwner != null && focusOwner != myFocusOwner && !(focusOwner instanceof Window)) {
        exitSuspendMode();
      }
    });

    addDispatcher(new WindowsAltSuppressor(), null);

    abracadabraDaberBoreh();
  }

  private void abracadabraDaberBoreh() {
    // we need to track if there are KeyBoardEvents in IdeEventQueue
    // so we want to intercept all events posted to IdeEventQueue and increment counters
    // However, we regular control flow goes like this:
    //    PostEventQueue.flush() -> EventQueue.postEvent() -> IdeEventQueue.postEventPrivate() -> AAAA we missed event, because postEventPrivate() can't be overridden.
    // Instead, we do following:
    //  - create new PostEventQueue holding our IdeEventQueue instead of old EventQueue
    //  - replace "PostEventQueue" value in AppContext with this new PostEventQueue
    // since that the control flow goes like this:
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

  public void setWindowManager(final WindowManagerEx windowManager) {
    myWindowManager = windowManager;
  }


  private void addIdleTimeCounterRequest() {
    if (isTestMode()) return;

    myIdleTimeCounterAlarm.cancelAllRequests();
    myLastActiveTime = System.currentTimeMillis();
    myIdleTimeCounterAlarm.addRequest(() -> {
      myIdleTime += System.currentTimeMillis() - myLastActiveTime;
      addIdleTimeCounterRequest();
    }, 20000, ModalityState.NON_MODAL);
  }

  /**
   * This class performs special processing in order to have {@link #getIdleTime()} return more or less up-to-date data.
   * <p/>
   * This method allows to stop that processing (convenient in non-intellij environment like upsource).
   */
  @SuppressWarnings("unused") // Used in upsource.
  public void stopIdleTimeCalculation() {
    myIdleTimeCounterAlarm.cancelAllRequests();
  }

  public boolean shouldNotTypeInEditor() {
    return myKeyEventDispatcher.isWaitingForSecondKeyStroke() || mySuspendMode;
  }


  private void enterSuspendMode() {
    mySuspendMode = true;
    myFocusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    mySuspendModeAlarm.cancelAllRequests();
    mySuspendModeAlarm.addRequest(myExitSuspendModeRunnable, 750);
  }

  /**
   * Exits suspend mode and pumps all suspended events.
   */
  private void exitSuspendMode() {
    if (shallEnterSuspendMode()) {
      // We have to exit from suspend mode (focus owner changes or alarm is triggered) but
      // WINDOW_OPENED isn't dispatched yet. In this case we have to restart the alarm until
      // all WINDOW_OPENED event will be processed.
      mySuspendModeAlarm.cancelAllRequests();
      mySuspendModeAlarm.addRequest(myExitSuspendModeRunnable, 250);
    }
    else {
      // Now we can pump all suspended events.
      mySuspendMode = false;
      myFocusOwner = null; // to prevent memory leaks
    }
  }


  public void addIdleListener(@NotNull final Runnable runnable, final int timeout) {
    LOG.assertTrue(timeout > 0);
    synchronized (myLock) {
      myIdleListeners.add(runnable);
      final MyFireIdleRequest request = new MyFireIdleRequest(runnable, timeout);
      myListener2Request.put(runnable, request);
      UIUtil.invokeLaterIfNeeded(() -> myIdleRequestsAlarm.addRequest(request, timeout));
    }
  }

  public void removeIdleListener(@NotNull final Runnable runnable) {
    synchronized (myLock) {
      final boolean wasRemoved = myIdleListeners.remove(runnable);
      if (!wasRemoved) {
        LOG.error("unknown runnable: " + runnable);
      }
      final MyFireIdleRequest request = myListener2Request.remove(runnable);
      LOG.assertTrue(request != null);
      myIdleRequestsAlarm.cancelRequest(request);
    }
  }

  /** @deprecated use {@link #addActivityListener(Runnable, Disposable)} (to be removed in IDEA 17) */
  @SuppressWarnings("unused")
  public void addActivityListener(@NotNull final Runnable runnable) {
    synchronized (myLock) {
      myActivityListeners.add(runnable);
    }
  }

  public void addActivityListener(@NotNull final Runnable runnable, Disposable parentDisposable) {
    synchronized (myLock) {
      ContainerUtil.add(runnable, myActivityListeners, parentDisposable);
    }
  }

  public void removeActivityListener(@NotNull final Runnable runnable) {
    synchronized (myLock) {
      myActivityListeners.remove(runnable);
    }
  }


  public void addDispatcher(final EventDispatcher dispatcher, Disposable parent) {
    _addProcessor(dispatcher, parent, myDispatchers);
  }

  public void removeDispatcher(EventDispatcher dispatcher) {
    myDispatchers.remove(dispatcher);
  }

  public boolean containsDispatcher(EventDispatcher dispatcher) {
    return myDispatchers.contains(dispatcher);
  }

  public void addPostprocessor(EventDispatcher dispatcher, @Nullable Disposable parent) {
    _addProcessor(dispatcher, parent, myPostProcessors);
  }

  public void removePostprocessor(EventDispatcher dispatcher) {
    myPostProcessors.remove(dispatcher);
  }

  private static void _addProcessor(final EventDispatcher dispatcher, Disposable parent, final Set<EventDispatcher> set) {
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

  public AWTEvent getTrueCurrentEvent() {
    return myCurrentEvent;
  }

  private static class InertialMouseRouter {
    private static final int MOUSE_WHEEL_RESTART_THRESHOLD = 50;
    private static Component wheelDestinationComponent;
    private static long lastMouseWheel;

    private static AWTEvent changeSourceIfNeeded(AWTEvent awtEvent) {
      if (SystemInfo.isMac && Registry.is("ide.inertial.mouse.fix") && awtEvent instanceof MouseWheelEvent) {
        MouseWheelEvent mwe = (MouseWheelEvent) awtEvent;
        if (mwe.getWhen() - lastMouseWheel > MOUSE_WHEEL_RESTART_THRESHOLD) {
          wheelDestinationComponent = SwingUtilities.getDeepestComponentAt(mwe.getComponent(), mwe.getX(), mwe.getY());
        }
        lastMouseWheel = System.currentTimeMillis();

        int modifiers = mwe.getModifiers() | mwe.getModifiersEx();
        return MouseEventAdapter.convert(mwe, wheelDestinationComponent, mwe.getID(), lastMouseWheel, modifiers, mwe.getX(), mwe.getY());
      }
      return awtEvent;
    }
  }

  private static boolean ourAppIsLoaded;

  private static boolean appIsLoaded() {
    if (ourAppIsLoaded) return true;
    boolean loaded = IdeaApplication.isLoaded();
    if (loaded) {
      ourAppIsLoaded = true;
    }
    return loaded;
  }

  @Override
  public void dispatchEvent(@NotNull AWTEvent e) {
    if (!appIsLoaded()) {
      try {
        super.dispatchEvent(e);
      }
      catch (Throwable t) {
        processException(t);
      }
      return;
    }

    e = InertialMouseRouter.changeSourceIfNeeded(e);

    e = fixNonEnglishKeyboardLayouts(e);

    e = mapEvent(e);
    if (Registry.is("keymap.windows.as.meta")) {
      e = mapMetaState(e);
    }

    boolean wasInputEvent = myIsInInputEvent;
    myIsInInputEvent = e instanceof InputEvent || e instanceof InputMethodEvent || e instanceof WindowEvent || e instanceof ActionEvent;
    if (myIsInInputEvent) {
      HeavyProcessLatch.INSTANCE.prioritizeUiActivity();
    } else {
      HeavyProcessLatch.INSTANCE.stopThreadPrioritizing();
    }
    AWTEvent oldEvent = myCurrentEvent;
    myCurrentEvent = e;

    boolean userActivity = myIsInInputEvent || e instanceof ItemEvent || e instanceof FocusEvent;
    try (AccessToken ignored = startActivity(userActivity)) {
      _dispatchEvent(e, false);
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
    }
  }

  @Override
  public AWTEvent getNextEvent() throws InterruptedException {
    AWTEvent event = super.getNextEvent();
    if (isKeyboardEvent(event) && myKeyboardEventsDispatched.incrementAndGet() > myKeyboardEventsPosted.get()) {
      throw new RuntimeException(event + "; posted: " + myKeyboardEventsPosted + "; dispatched: " + myKeyboardEventsDispatched);
    }
    return event;
  }

  @Nullable
  private static AccessToken startActivity(boolean userActivity) {
    if (ourTransactionGuard == null && appIsLoaded()) {
      ourTransactionGuard = (TransactionGuardImpl)TransactionGuard.getInstance();
    }
    return ourTransactionGuard == null ? null : ourTransactionGuard.startActivity(userActivity);
  }

  private void processException(Throwable t) {
    if (!myToolkitBugsProcessor.process(t)) {
      PluginManager.processException(t);
    }
  }

  private static int ctrlIsPressedCount;
  private static boolean leftAltIsPressed;
  //private static boolean altGrIsPressed = false;

  private static AWTEvent fixNonEnglishKeyboardLayouts(AWTEvent e) {
    if (!(e instanceof KeyEvent)) return e;

    KeyboardSettingsExternalizable externalizable = KeyboardSettingsExternalizable.getInstance();
    if (!Registry.is("ide.non.english.keyboard.layout.fix") || externalizable == null || !externalizable.isNonEnglishKeyboardSupportEnabled()) return e;

    KeyEvent ke = (KeyEvent)e;

    // Try to get it from editor
    Component sourceComponent = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();

    switch (ke.getID()) {
      case KeyEvent.KEY_PRESSED:
        break;
      case KeyEvent.KEY_RELEASED:
        break;
    }

    //if (!leftAltIsPressed && KeyboardSettingsExternalizable.getInstance().isUkrainianKeyboard(sourceComponent)) {
    //  if ('ґ' == ke.getKeyChar() || ke.getKeyCode() == KeyEvent.VK_U) {
    //    ke = new KeyEvent(ke.getComponent(), ke.getID(), ke.getWhen(), 0,
    //                     KeyEvent.VK_UNDEFINED, 'ґ', ke.getKeyLocation());
    //    ke.setKeyCode(KeyEvent.VK_U);
    //    ke.setKeyChar('ґ');
    //    return ke;
    //  }
    //}

    // NB: Standard keyboard layout is an English keyboard layout. If such
    //     layout is active every KeyEvent that is received has
    //     a @{code KeyEvent.getKeyCode} key code corresponding to
    //     the @{code KeyEvent.getKeyChar} key char in the event.
    //     For  example, VK_MINUS key code and '-' character
    //
    // We have a key char. On some non standard layouts it does not correspond to
    // key code in the event.

    Integer keyCodeFromChar = CharToVKeyMap.get(ke.getKeyChar());

    // Now we have a correct key code as if we'd gotten  a KeyEvent for
    // standard English layout

    if (keyCodeFromChar == ke.getKeyCode() || keyCodeFromChar == KeyEvent.VK_UNDEFINED) {
      return e;
    }

    // Farther we handle a non standard layout

    if (keyCodeFromChar != null) {
      if (keyCodeFromChar != ke.getKeyCode()) {
        // non-english layout
        ke.setKeyCode(keyCodeFromChar);
      }
      }

    return ke;
  }

  private static AWTEvent mapEvent(AWTEvent e) {
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

  private AWTEvent mapMetaState(AWTEvent e) {
    if (myWinMetaPressed) {
      Application app = ApplicationManager.getApplication();

      boolean weAreNotActive = app == null || !app.isActive();
      weAreNotActive |= e instanceof FocusEvent && ((FocusEvent)e).getOppositeComponent() == null;
      if (weAreNotActive) {
        myWinMetaPressed = false;
        return e;
      }
    }

    if (e instanceof KeyEvent) {
      KeyEvent ke = (KeyEvent)e;
      if (ke.getKeyCode() == KeyEvent.VK_WINDOWS) {
        if (ke.getID() == KeyEvent.KEY_PRESSED) myWinMetaPressed = true;
        if (ke.getID() == KeyEvent.KEY_RELEASED) myWinMetaPressed = false;
        return new KeyEvent(ke.getComponent(), ke.getID(), ke.getWhen(), ke.getModifiers() | ke.getModifiersEx(), KeyEvent.VK_META, ke.getKeyChar(),
                            ke.getKeyLocation());
      }
      if (myWinMetaPressed) {
        return new KeyEvent(ke.getComponent(), ke.getID(), ke.getWhen(), ke.getModifiers() | ke.getModifiersEx() | InputEvent.META_MASK, ke.getKeyCode(),
                            ke.getKeyChar(), ke.getKeyLocation());
      }
    }

    if (myWinMetaPressed && e instanceof MouseEvent && ((MouseEvent)e).getButton() != 0) {
      MouseEvent me = (MouseEvent)e;
      return new MouseEvent(me.getComponent(), me.getID(), me.getWhen(), me.getModifiers() | me.getModifiersEx() | InputEvent.META_MASK, me.getX(), me.getY(),
                            me.getClickCount(), me.isPopupTrigger(), me.getButton());
    }

    return e;
  }

  public void _dispatchEvent(@NotNull AWTEvent e, boolean typeAheadFlushing) {
    if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
      DnDManagerImpl dndManager = (DnDManagerImpl)DnDManager.getInstance();
      if (dndManager != null) {
        dndManager.setLastDropHandler(null);
      }
    }

    myEventCount++;

    if (processAppActivationEvents(e)) return;

    if (!typeAheadFlushing) {
      fixStickyFocusedComponents(e);
    }

    if (!myPopupManager.isPopupActive()) {
      enterSuspendModeIfNeeded(e);
    }

    myKeyboardBusy = e instanceof KeyEvent || myKeyboardEventsPosted.get() > myKeyboardEventsDispatched.get();

    if (e instanceof KeyEvent) {
      if (e.getID() == KeyEvent.KEY_RELEASED && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_SHIFT) {
        myMouseEventDispatcher.resetHorScrollingTracker();
      }
    }

    if (!typeAheadFlushing && typeAheadDispatchToFocusManager(e)) {
      return;
    }

    if (e instanceof WindowEvent) {
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
          final MyFireIdleRequest request = myListener2Request.get(idleListener);
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
          addIdleTimeCounterRequest();
          for (Runnable activityListener : myActivityListeners) {
            activityListener.run();
          }
        }
      }
    }
    if (myPopupManager.isPopupActive() && myPopupManager.dispatch(e)) {
      if (myKeyEventDispatcher.isWaitingForSecondKeyStroke()) {
        myKeyEventDispatcher.setState(KeyState.STATE_INIT);
      }

      return;
    }

    for (EventDispatcher eachDispatcher : myDispatchers) {
      if (eachDispatcher.dispatch(e)) {
        return;
      }
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
      if (mySuspendMode || !myKeyEventDispatcher.dispatchKeyEvent((KeyEvent)e)) {
        defaultDispatchEvent(e);
      }
      else {
        ((KeyEvent)e).consume();
        defaultDispatchEvent(e);
      }
    }
    else if (e instanceof MouseEvent) {
      MouseEvent me = (MouseEvent)e;
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

  private static void fixStickyWindow(KeyboardFocusManager mgr, Window wnd, String resetMethod) {
    if (wnd != null && !wnd.isShowing()) {
      Window showingWindow = wnd;
      while (showingWindow != null) {
        if (showingWindow.isShowing()) break;
        showingWindow = (Window)showingWindow.getParent();
      }

      if (showingWindow == null) {
        final Frame[] allFrames = Frame.getFrames();
        for (Frame each : allFrames) {
          if (each.isShowing()) {
            showingWindow = each;
            break;
          }
        }
      }


      if (showingWindow != null && showingWindow != wnd) {
        final Method setActive = ReflectionUtil.findMethod(ReflectionUtil.getClassDeclaredMethods(KeyboardFocusManager.class, false), resetMethod, Window.class);
        if (setActive != null) {
          try {
            setActive.invoke(mgr, (Window)showingWindow);
          }
          catch (Exception exc) {
            LOG.info(exc);
          }
        }
      }
    }
  }

  public void fixStickyFocusedComponents(@Nullable AWTEvent e) {
    if (e != null && !(e instanceof InputEvent)) return;

    final KeyboardFocusManager mgr = KeyboardFocusManager.getCurrentKeyboardFocusManager();

    if (Registry.is("actionSystem.fixStickyFocusedWindows")) {
      fixStickyWindow(mgr, mgr.getActiveWindow(), "setGlobalActiveWindow");
      fixStickyWindow(mgr, mgr.getFocusedWindow(), "setGlobalFocusedWindow");
    }

    if (Registry.is("actionSystem.fixNullFocusedComponent")) {
      final Component focusOwner = mgr.getFocusOwner();
      if (focusOwner == null || !focusOwner.isShowing() || focusOwner instanceof JFrame || focusOwner instanceof JDialog) {

        final Application app = ApplicationManager.getApplication();
        if (app instanceof ApplicationEx && !((ApplicationEx) app).isLoaded()) {
          return;
        }

        boolean mouseEventsAhead = isMouseEventAhead(e);
        boolean focusTransferredNow = IdeFocusManager.getGlobalInstance().isFocusBeingTransferred();

        boolean okToFixFocus = !mouseEventsAhead && !focusTransferredNow;

        if (okToFixFocus) {
          Window showingWindow = mgr.getActiveWindow();
          if (showingWindow == null) {
            Method getNativeFocusOwner = ReflectionUtil.getDeclaredMethod(KeyboardFocusManager.class, "getNativeFocusOwner");
            if (getNativeFocusOwner != null) {
              try {
                Object owner = getNativeFocusOwner.invoke(mgr);
                if (owner instanceof Component) {
                  showingWindow = UIUtil.getWindow((Component)owner);
                }
              }
              catch (Exception e1) {
                LOG.debug(e1);
              }
            }
          }
          if (showingWindow != null) {
            final IdeFocusManager fm = IdeFocusManager.findInstanceByComponent(showingWindow);
            ExpirableRunnable maybeRequestDefaultFocus = new ExpirableRunnable() {
              @Override
              public void run() {
                if (getPopupManager().requestDefaultFocus(false)) return;

                final Application app = ApplicationManager.getApplication();
                if (app != null && app.isActive()) {
                  fm.requestDefaultFocus(false);
                }
              }

              @Override
              public boolean isExpired() {
                return !UIUtil.isMeaninglessFocusOwner(mgr.getFocusOwner());
              }
            };
            fm.revalidateFocus(maybeRequestDefaultFocus);
          }
        }
      }
    }
  }

  public static boolean isMouseEventAhead(@Nullable AWTEvent e) {
    IdeEventQueue queue = getInstance();
    return e instanceof MouseEvent ||
           queue.peekEvent(MouseEvent.MOUSE_PRESSED) != null ||
           queue.peekEvent(MouseEvent.MOUSE_RELEASED) != null ||
           queue.peekEvent(MouseEvent.MOUSE_CLICKED) != null;
  }

  private void enterSuspendModeIfNeeded(AWTEvent e) {
    if (e instanceof KeyEvent) {
      if (!mySuspendMode && shallEnterSuspendMode()) {
        enterSuspendMode();
      }
    }
  }

  private boolean shallEnterSuspendMode() {
    return peekEvent(WindowEvent.WINDOW_OPENED) != null;
  }

  private static boolean processAppActivationEvents(AWTEvent e) {

    if (e instanceof WindowEvent) {
      final WindowEvent we = (WindowEvent)e;

      ApplicationActivationStateManager.updateState(we);

      storeLastFocusedComponent(we);
    }

    return false;
  }

  private static void storeLastFocusedComponent(WindowEvent we) {
    final Window eventWindow = we.getWindow();

    if (we.getID() == WindowEvent.WINDOW_DEACTIVATED || we.getID() == WindowEvent.WINDOW_LOST_FOCUS) {
      Component frame = UIUtil.findUltimateParent(eventWindow);
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

  private void defaultDispatchEvent(final AWTEvent e) {
    try {
      myDispatchingFocusEvent = e instanceof FocusEvent;

      maybeReady();
      fixStickyAlt(e);

      super.dispatchEvent(e);
    }
    catch (Throwable t) {
      processException(t);
    }
    finally {
      myDispatchingFocusEvent = false;
    }
  }

  private static Field ourStickyAltField;

  private static void fixStickyAlt(AWTEvent e) {
    if (Registry.is("actionSystem.win.suppressAlt.new")) {
      if (UIUtil.isUnderWindowsLookAndFeel() &&
          e instanceof InputEvent &&
          (((InputEvent)e).getModifiers() & (InputEvent.ALT_MASK | InputEvent.ALT_DOWN_MASK)) != 0 &&
          !(e instanceof KeyEvent && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_ALT)) {
        try {
          if (ourStickyAltField == null) {
            Class<?> aClass = Class.forName("com.sun.java.swing.plaf.windows.WindowsRootPaneUI$AltProcessor");
            ourStickyAltField = ReflectionUtil.getDeclaredField(aClass, "menuCanceledOnPress");
          }
          if (ourStickyAltField != null) {
            ourStickyAltField.set(null, true);
          }
        }
        catch (Exception exception) {
          LOG.error(exception);
        }
      }
    }
    else if (SystemInfo.isWinXpOrNewer && !SystemInfo.isWinVistaOrNewer && e instanceof KeyEvent && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_ALT) {
      ((KeyEvent)e).consume();  // IDEA-17359
    }
  }

  public boolean isDispatchingFocusEvent() {
    return myDispatchingFocusEvent;
  }

  private static boolean typeAheadDispatchToFocusManager(AWTEvent e) {
    if (e instanceof KeyEvent) {
      final KeyEvent event = (KeyEvent)e;
      if (!event.isConsumed()) {
        final IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(event.getComponent());
        return focusManager.dispatch(event);
      }
    }

    return false;
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

  public void pumpEventsForHierarchy(Component modalComponent, Condition<AWTEvent> exitCondition) {
    AWTEvent event;
    do {
      try {
        event = getNextEvent();
        boolean eventOk = true;
        if (event instanceof InputEvent) {
          final Object s = event.getSource();
          if (s instanceof Component) {
            Component c = (Component)s;
            Window modalWindow = modalComponent == null ? null : SwingUtilities.windowForComponent(modalComponent);
            while (c != null && c != modalWindow) c = c.getParent();
            if (c == null) {
              eventOk = false;
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
  }

  @FunctionalInterface
  public interface EventDispatcher {
    boolean dispatch(AWTEvent e);
  }

  private final class MyFireIdleRequest implements Runnable {
    private final Runnable myRunnable;
    private final int myTimeout;


    public MyFireIdleRequest(@NotNull Runnable runnable, final int timeout) {
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
      return "Fire idle request. delay: "+getTimeout()+"; runnable: "+myRunnable;
    }
  }

  private final class ExitSuspendModeRunnable implements Runnable {

    @Override
    public void run() {
      if (mySuspendMode) {
        exitSuspendMode();
      }
    }
  }


  public long getIdleTime() {
    return myIdleTime;
  }


  public IdePopupManager getPopupManager() {
    return myPopupManager;
  }

  public IdeKeyEventDispatcher getKeyEventDispatcher() {
    return myKeyEventDispatcher;
  }

  /**
   * Same as {@link #blockNextEvents(MouseEvent, IdeEventQueue.BlockMode)} with <code>blockMode</code> equal to <code>COMPLETE</code>.
   */
  public void blockNextEvents(final MouseEvent e) {
    blockNextEvents(e, BlockMode.COMPLETE);
  }

  /**
   * When <code>blockMode</code> is <code>COMPLETE</code>, blocks following related mouse events completely, when <code>blockMode</code> is
   * <code>ACTIONS</code> only blocks performing actions bound to corresponding mouse shortcuts.
   */
  public void blockNextEvents(final MouseEvent e, BlockMode blockMode) {
    myMouseEventDispatcher.blockNextEvents(e, blockMode);
  }

  public boolean isSuspendMode() {
    return mySuspendMode;
  }

  public boolean hasFocusEventsPending() {
    return peekEvent(FocusEvent.FOCUS_GAINED) != null || peekEvent(FocusEvent.FOCUS_LOST) != null;
  }

  private boolean isReady() {
    return !myKeyboardBusy && myKeyEventDispatcher.isReady();
  }

  public void maybeReady() {
    flushReady();
  }

  private void flushReady() {
    if (myReady.isEmpty() || !isReady()) return;

    Runnable[] ready = myReady.toArray(new Runnable[myReady.size()]);
    myReady.clear();

    for (Runnable each : ready) {
      each.run();
    }
  }

  public void doWhenReady(final Runnable runnable) {
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
    public boolean dispatch(AWTEvent e) {
      boolean dispatch = true;
      if (e instanceof KeyEvent) {
        KeyEvent ke = (KeyEvent)e;
        final Component component = ke.getComponent();
        boolean pureAlt = ke.getKeyCode() == KeyEvent.VK_ALT && (ke.getModifiers() | InputEvent.ALT_MASK) == InputEvent.ALT_MASK;
        if (!pureAlt) {
          myWaitingForAltRelease = false;
        }
        else {
          if (ApplicationManager.getApplication() == null ||
              UISettings.getInstance() == null ||
              !SystemInfo.isWindows ||
              !Registry.is("actionSystem.win.suppressAlt") ||
              !(UISettings.getInstance().HIDE_TOOL_STRIPES || UISettings.getInstance().PRESENTATION_MODE)) {
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
                  final Window window = UIUtil.getWindow(component);
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

  public boolean isInputMethodEnabled() {
    return !SystemInfo.isMac || myInputMethodLock == 0;
  }

  public void disableInputMethods(Disposable parentDisposable) {
    myInputMethodLock++;
    Disposer.register(parentDisposable, () -> myInputMethodLock--);
  }

  private final FrequentEventDetector myFrequentEventDetector = new FrequentEventDetector(1009, 100);
  @Override
  public void postEvent(@NotNull AWTEvent event) {
    myFrequentEventDetector.eventHappened(event);
    if (isKeyboardEvent(event)) {
      myKeyboardEventsPosted.incrementAndGet();
    }
    super.postEvent(event);
  }

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
}
