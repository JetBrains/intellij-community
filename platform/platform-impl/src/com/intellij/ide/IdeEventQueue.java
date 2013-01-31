/*
 * Copyright 2000-2012 JetBrains s.r.o.
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


import com.intellij.Patches;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDManagerImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher;
import com.intellij.openapi.keymap.impl.KeyState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ExpirableRunnable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.Alarm;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;


/**
 * @author Vladimir Kondratyev
 * @author Anton Katilin
 */

public class IdeEventQueue extends EventQueue {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.IdeEventQueue");

  /**
   * Adding/Removing of "idle" listeners should be thread safe.
   */
  private final Object myLock = new Object();

  private final ArrayList<Runnable> myIdleListeners = new ArrayList<Runnable>(2);

  private final ArrayList<Runnable> myActivityListeners = new ArrayList<Runnable>(2);

  private final Alarm myIdleRequestsAlarm = new Alarm();

  private final Alarm myIdleTimeCounterAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private long myIdleTime;

  private final Map<Runnable, MyFireIdleRequest> myListener2Request = new HashMap<Runnable, MyFireIdleRequest>();
  // IdleListener -> MyFireIdleRequest

  private final IdeKeyEventDispatcher myKeyEventDispatcher = new IdeKeyEventDispatcher(this);

  private final IdeMouseEventDispatcher myMouseEventDispatcher = new IdeMouseEventDispatcher();

  private final IdePopupManager myPopupManager = new IdePopupManager();


  private final ToolkitBugsProcessor myToolkitBugsProcessor = new ToolkitBugsProcessor();

  private boolean mySuspendMode;

  /**
   * We exit from suspend mode when focus owner changes and no more WindowEvent.WINDOW_OPENED events
   * <p/>
   * in the queue. If WINDOW_OPENED event does exists in the queus then we restart the alarm.
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


  private boolean myIsInInputEvent = false;

  private AWTEvent myCurrentEvent = null;

  private long myLastActiveTime;

  private WindowManagerEx myWindowManager;


  private final Set<EventDispatcher> myDispatchers = new LinkedHashSet<EventDispatcher>();
  private final Set<EventDispatcher> myPostprocessors = new LinkedHashSet<EventDispatcher>();

  private final Set<Runnable> myReady = new HashSet<Runnable>();
  private boolean myKeyboardBusy;
  private boolean myDispatchingFocusEvent;

  private int myInputMethodLock;

  private static class IdeEventQueueHolder {
    private static final IdeEventQueue INSTANCE = new IdeEventQueue();
  }

  public static IdeEventQueue getInstance() {
    return IdeEventQueueHolder.INSTANCE;
  }

  private IdeEventQueue() {
    addIdleTimeCounterRequest();
    final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();

    //noinspection HardCodedStringLiteral
    keyboardFocusManager.addPropertyChangeListener("permanentFocusOwner", new PropertyChangeListener() {

      @Override
      public void propertyChange(final PropertyChangeEvent e) {
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
      }
    });

    addDispatcher(new WindowsAltSupressor(), null);
  }


  public void setWindowManager(final WindowManagerEx windowManager) {
    myWindowManager = windowManager;
  }


  private void addIdleTimeCounterRequest() {
    Application application = ApplicationManager.getApplication();
    if (application != null && application.isUnitTestMode()) return;

    myIdleTimeCounterAlarm.cancelAllRequests();
    myLastActiveTime = System.currentTimeMillis();
    myIdleTimeCounterAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        myIdleTime += System.currentTimeMillis() - myLastActiveTime;
        addIdleTimeCounterRequest();
      }
    }, 20000, ModalityState.NON_MODAL);
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
   * Exits supend mode and pumps all suspended events.
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
      myIdleRequestsAlarm.addRequest(request, timeout);
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
    _addProcessor(dispatcher, parent, myPostprocessors);
  }

  public void removePostprocessor(EventDispatcher dispatcher) {
    myPostprocessors.remove(dispatcher);
  }

  private static void _addProcessor(final EventDispatcher dispatcher, Disposable parent, final Set<EventDispatcher> set) {
    set.add(dispatcher);
    if (parent != null) {
      Disposer.register(parent, new Disposable() {
        @Override
        public void dispose() {
          set.remove(dispatcher);
        }
      });
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

  @Override
  public void dispatchEvent(final AWTEvent e) {
    boolean wasInputEvent = myIsInInputEvent;
    myIsInInputEvent = e instanceof InputEvent || e instanceof InputMethodEvent || e instanceof WindowEvent || e instanceof ActionEvent;
    AWTEvent oldEvent = myCurrentEvent;
    myCurrentEvent = e;

    try {
      _dispatchEvent(e, false);
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Throwable exc) {
      if (!myToolkitBugsProcessor.process(exc)) {
        LOG.error("Error during dispatching of " + e, exc);
      }
    }
    finally {
      myIsInInputEvent = wasInputEvent;
      myCurrentEvent = oldEvent;

      for (EventDispatcher each : myPostprocessors) {
        each.dispatch(e);
      }

      if (e instanceof KeyEvent) {
        maybeReady();
      }
    }
  }

  @SuppressWarnings({"ALL"})
  private static String toDebugString(final AWTEvent e) {
    if (e instanceof InvocationEvent) {
      try {
        final Field f = InvocationEvent.class.getDeclaredField("runnable");
        f.setAccessible(true);
        Object runnable = f.get(e);

        return "Invoke Later[" + runnable.toString() + "]";
      }
      catch (NoSuchFieldException e1) {
      }
      catch (IllegalAccessException e1) {
      }
    }
    return e.toString();
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

    myKeyboardBusy = e instanceof KeyEvent ||
                     peekEvent(KeyEvent.KEY_PRESSED) != null ||
                     peekEvent(KeyEvent.KEY_RELEASED) != null ||
                     peekEvent(KeyEvent.KEY_TYPED) != null;

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
      if (selectedPath != null && selectedPath.length > 0 && !(selectedPath[0] instanceof ComboPopup)) {
        ((MouseWheelEvent)e).consume();
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
          int timeout = request.getTimeout();
          myIdleRequestsAlarm.addRequest(request, timeout, ModalityState.NON_MODAL);
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
    if (e instanceof InputEvent && Patches.SPECIAL_INPUT_METHOD_PROCESSING) {
      final InputEvent inputEvent = (InputEvent)e;
      if (!inputEvent.getComponent().isShowing()) {
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
      if (myMouseEventDispatcher.patchClickCount(me) && me.getID() == MouseEvent.MOUSE_CLICKED) {
        final MouseEvent toDispatch =
          new MouseEvent(me.getComponent(), me.getID(), System.currentTimeMillis(), me.getModifiers(), me.getX(), me.getY(), 1,
                         me.isPopupTrigger(), me.getButton());
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            dispatchEvent(toDispatch);
          }
        });
      }
      if (me.getButton() != 0) {
        setLastClickEvent(me);
      } else if (lastClickEvent != null && Math.abs(System.currentTimeMillis() - lastClickTime) > 200){
        setLastClickEvent(null);//Obsolete event
      }
      if (!myMouseEventDispatcher.dispatchMouseEvent(me)) {
        defaultDispatchEvent(e);
      }
    }
    else {
      defaultDispatchEvent(e);
    }
  }

  private MouseEvent lastClickEvent = null;
  private long lastClickTime = 0L;

  private void setLastClickEvent(@Nullable MouseEvent event) {
    lastClickEvent = event;
    lastClickTime = System.currentTimeMillis();
  }

  public boolean wasRootRecentlyClicked(Component component) {
    if (component == null || lastClickEvent == null || lastClickEvent.getComponent() == null)
      return false;
    return SwingUtilities.getRoot(lastClickEvent.getComponent()) == SwingUtilities.getRoot(component);
  }

  private static void fixStickyWindow(KeyboardFocusManager mgr, Window wnd, String resetMethod) {
    Window showingWindow = wnd;

    if (wnd != null && !wnd.isShowing()) {
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
        final Method setActive = ReflectionUtil.findMethod(KeyboardFocusManager.class.getDeclaredMethods(), resetMethod, Window.class);
        if (setActive != null) {
          try {
            setActive.setAccessible(true);
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
              getNativeFocusOwner.setAccessible(true);
              try {
                Object owner = getNativeFocusOwner.invoke(mgr);
                if (owner instanceof Component) {
                  Component nativeFocusOwner = (Component)owner;
                  if (nativeFocusOwner instanceof Window) {
                    showingWindow = (Window)nativeFocusOwner;
                  } else {
                    showingWindow = SwingUtilities.getWindowAncestor(nativeFocusOwner);
                  }
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
    final Application app = ApplicationManager.getApplication();
    if (!(app instanceof ApplicationImpl)) return false;

    ApplicationImpl appImpl = (ApplicationImpl)app;

    boolean consumed = false;
    if (e instanceof WindowEvent) {
      WindowEvent we = (WindowEvent)e;
      if (we.getID() == WindowEvent.WINDOW_GAINED_FOCUS && we.getWindow() != null) {
        if (we.getOppositeWindow() == null && !appImpl.isActive()) {
          consumed = appImpl.tryToApplyActivationState(true, we.getWindow());
        }
      }
      else if (we.getID() == WindowEvent.WINDOW_LOST_FOCUS && we.getWindow() != null) {
        if (we.getOppositeWindow() == null && appImpl.isActive()) {
          consumed = appImpl.tryToApplyActivationState(false, we.getWindow());
        }
      }
    }

    return false;
  }


  private void defaultDispatchEvent(final AWTEvent e) {
    try {
      myDispatchingFocusEvent = e instanceof FocusEvent;

      maybeReady();

      super.dispatchEvent(e);
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Throwable exc) {
      if (!myToolkitBugsProcessor.process(exc)) {
        LOG.error("Error during dispatching of " + e, exc);
      }
    } finally {
      myDispatchingFocusEvent = false;
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

  public void blockNextEvents(final MouseEvent e) {
    myMouseEventDispatcher.blockNextEvents(e);
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
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          myReady.add(runnable);
          maybeReady();
        }
      });
    }
  }

  public boolean isPopupActive() {
    return myPopupManager.isPopupActive();
  }

  private static class WindowsAltSupressor implements EventDispatcher {

    private boolean myPureAltWasPressed;
    private boolean myWaitingForAltRelease;
    private boolean myWaiterScheduled;

    private Robot myRobot;

    @Override
    public boolean dispatch(AWTEvent e) {
      boolean dispatch = true;
      if (e instanceof KeyEvent) {
        KeyEvent ke = (KeyEvent)e;
        boolean pureAlt = ke.getKeyCode() == KeyEvent.VK_ALT && (ke.getModifiers() | InputEvent.ALT_MASK) == InputEvent.ALT_MASK;
        if (!pureAlt) {
          myPureAltWasPressed = false;
          myWaitingForAltRelease = false;
          myWaiterScheduled = false;
        }
        else {
          Application app = ApplicationManager.getApplication();
          if (app == null ||
              !SystemInfo.isWindows ||
              !Registry.is("actionSystem.win.suppressAlt") ||
              !UISettings.getInstance().HIDE_TOOL_STRIPES) {
            return !dispatch;
          }

          if (ke.getID() == KeyEvent.KEY_PRESSED) {
            myPureAltWasPressed = true;
            dispatch = !myWaitingForAltRelease;
          }
          else if (ke.getID() == KeyEvent.KEY_RELEASED) {
            if (myWaitingForAltRelease) {
              myPureAltWasPressed = false;
              myWaitingForAltRelease = false;
              myWaiterScheduled = false;
              dispatch = false;
            }
            else {
              myWaiterScheduled = true;
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  try {
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
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myInputMethodLock--;
      }
    });
  }
}
