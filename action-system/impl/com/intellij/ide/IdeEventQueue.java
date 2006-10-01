package com.intellij.ide;



import com.intellij.Patches;

import com.intellij.openapi.application.Application;

import com.intellij.openapi.application.ApplicationManager;

import com.intellij.openapi.application.ModalityState;

import com.intellij.openapi.diagnostic.Logger;

import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;

import com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher;

import com.intellij.openapi.util.Condition;

import com.intellij.openapi.util.SystemInfo;

import com.intellij.openapi.wm.ex.WindowManagerEx;

import com.intellij.util.Alarm;

import com.intellij.util.containers.HashMap;



import javax.swing.*;

import java.awt.*;

import java.awt.event.*;

import java.beans.PropertyChangeEvent;

import java.beans.PropertyChangeListener;

import java.util.ArrayList;



/**

 * @author Vladimir Kondratyev

 * @author Anton Katilin

 */

public class IdeEventQueue extends EventQueue {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.IdeEventQueue");



  private static IdeEventQueue ourInstance;



  /** Adding/Removing of "idle" listeners should be thread safe. */

  private final Object myLock;



  private final ArrayList<Runnable> myIdleListeners;

  private final ArrayList<Runnable> myActivityListeners;

  private final Alarm myIdleRequestsAlarm;

  private final Alarm myIdleTimeCounterAlarm;

  private long myIdleTime;

  private final HashMap<Runnable, MyFireIdleRequest> myListener2Request; // IdleListener -> MyFireIdleRequest

  private final HashMap<Runnable,Integer> myListener2Timeout; // IdleListener -> java.lang.Integer





  private final IdeKeyEventDispatcher myKeyEventDispatcher;

  private final IdeMouseEventDispatcher myMouseEventDispatcher;

  private final IdePopupManager myPopupManager;



  private EventDispatcher myDispatcher;



  private boolean mySuspendMode;

  /**

   * We exit from suspend mode when focus owner changes and no more WindowEvent.WINDOW_OPENED events

   * in the queue. If WINDOW_OPENED event does exists in the queus then we restart the alarm.

   */

  private Component myFocusOwner;

  private final Runnable myExitSuspendModeRunnable;

  /**

   * We exit from suspend mode when this alarm is triggered and no mode WindowEvent.WINDOW_OPENED

   * events in the queue. If WINDOW_OPENED event does exist then we restart the alarm.

   */

  private final Alarm mySuspendModeAlarm;

  /**

   * Counter of processed events. It is used to assert that data context lives only inside single

   * Swing event.

   */

  private int myEventCount;



  private boolean myIsInInputEvent = false;

  private AWTEvent myCurrentEvent = null;

  private long myLastActiveTime;

  private WindowManagerEx myWindowManager;



  public static IdeEventQueue getInstance() {

    if (ourInstance == null) {

      ourInstance = new IdeEventQueue();

    }

    return ourInstance;

  }



  private IdeEventQueue() {

    myLock = new Object();

    myIdleListeners = new ArrayList<Runnable>(2);

    myActivityListeners = new ArrayList<Runnable>(2);

    myIdleRequestsAlarm = new Alarm();

    myIdleTimeCounterAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    addIdleTimeCounterRequest();

    myListener2Request = new HashMap<Runnable, MyFireIdleRequest>();

    myListener2Timeout = new HashMap<Runnable, Integer>();

    myKeyEventDispatcher = new IdeKeyEventDispatcher();

    myMouseEventDispatcher = new IdeMouseEventDispatcher();

    myPopupManager = new IdePopupManager();

    myExitSuspendModeRunnable = new ExitSuspendModeRunnable();

    mySuspendModeAlarm = new Alarm();



    final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();

    //noinspection HardCodedStringLiteral

    keyboardFocusManager.addPropertyChangeListener(

      "permanentFocusOwner",

      new PropertyChangeListener() {

        public void propertyChange(final PropertyChangeEvent e) {

          final Application application = ApplicationManager.getApplication();

          if(application == null){

            // We can get focus event before application is initialized

            return;

          }

          application.assertIsDispatchThread();

          final Window focusedWindow = keyboardFocusManager.getFocusedWindow();

          final Component focusOwner = keyboardFocusManager.getFocusOwner();

          if (

            mySuspendMode &&

            focusedWindow != null &&

            focusOwner != null &&

            focusOwner != myFocusOwner &&

            !(focusOwner instanceof Window)

          ) {

            exitSuspendMode();

          }

        }

      }

    );

  }



  public void setWindowManager(final WindowManagerEx windowManager) {

    myWindowManager = windowManager;

  }



  private void addIdleTimeCounterRequest() {

    myIdleTimeCounterAlarm.cancelAllRequests();

    myLastActiveTime = System.currentTimeMillis();

    myIdleTimeCounterAlarm.addRequest(new Runnable() {

      public void run() {

        myIdleTime += (System.currentTimeMillis() - myLastActiveTime);

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

    if (peekEvent(WindowEvent.WINDOW_OPENED) != null) {

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



  public void addIdleListener(final Runnable runnable, final int timeout) {

    LOG.assertTrue(runnable != null);

    LOG.assertTrue(timeout > 0);

    synchronized (myLock) {

      myIdleListeners.add(runnable);

      final MyFireIdleRequest request = new MyFireIdleRequest(runnable);

      myListener2Request.put(runnable, request);

      myListener2Timeout.put(runnable, timeout);

      myIdleRequestsAlarm.addRequest(request, timeout);

    }

  }



  public void removeIdleListener(final Runnable runnable) {

    LOG.assertTrue(runnable != null);

    synchronized (myLock) {

      final boolean wasRemoved = myIdleListeners.remove(runnable);

      if (!wasRemoved) {

        LOG.assertTrue(false, "unknown runnable: " + runnable);

      }



      final MyFireIdleRequest request = myListener2Request.remove(runnable);

      LOG.assertTrue(request != null);

      myIdleRequestsAlarm.cancelRequest(request);



      final Integer timeout = myListener2Timeout.remove(runnable);

      LOG.assertTrue(timeout != null);

    }

  }



  public void addActivityListener(final Runnable runnable) {

    LOG.assertTrue(runnable != null);

    synchronized (myLock) {

      myActivityListeners.add(runnable);

    }

  }



  public void removeActivityListener(final Runnable runnable) {

    LOG.assertTrue(runnable != null);

    synchronized (myLock) {

      final boolean wasRemoved = myActivityListeners.remove(runnable);

      if (!wasRemoved) {

        LOG.assertTrue(false, "unknown runnable: " + runnable);

      }

    }

  }



  public void setDispatcher(final EventDispatcher dispatcher) {

    myDispatcher = dispatcher;

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



  //[jeka] commented for performance reasons

  /*

  public void postEvent(final AWTEvent e) {

    // [vova] sometime people call SwingUtilities.invokeLater(null). To

    // find such situations we will specially check InvokationEvents

    try {

      if (e instanceof InvocationEvent) {

        //noinspection HardCodedStringLiteral

        final Field field = InvocationEvent.class.getDeclaredField("runnable");

        field.setAccessible(true);

        final Object runnable = field.get(e);

        if (runnable == null) {

          //noinspection HardCodedStringLiteral

          throw new IllegalStateException("InvocationEvent contains null runnable: " + e);

        }

      }

    }

    catch (final Exception exc) {

      throw new Error(exc);

    }

    super.postEvent(e);

  }

  */



  public void dispatchEvent(final AWTEvent e) {

    boolean wasInputEvent = myIsInInputEvent;

    myIsInInputEvent = (e instanceof InputEvent) || (e instanceof InputMethodEvent) || (e instanceof WindowEvent) || (e instanceof ActionEvent);

    AWTEvent oldEvent = myCurrentEvent;

    myCurrentEvent = e;



    try{

      _dispatchEvent(e);

    }

    finally{

      myIsInInputEvent = wasInputEvent;

      myCurrentEvent = oldEvent;

    }

  }



  private void _dispatchEvent(final AWTEvent e) {



    myEventCount++;



    if (!myPopupManager.isPopupActive()) {

      // Enter to suspend mode if necessary. Suspend will cancel processing of actions mapped to the keyboard shortcuts.

      if (e instanceof KeyEvent) {

        if (!mySuspendMode && peekEvent(WindowEvent.WINDOW_OPENED) != null) {

          enterSuspendMode();

        }

      }

    }



    // Process "idle" and "activity" listeners



    if ((e instanceof KeyEvent) || (e instanceof MouseEvent)) {

      synchronized (myLock) {

        myIdleRequestsAlarm.cancelAllRequests();

        for (Runnable idleListener : myIdleListeners) {

          final MyFireIdleRequest request = myListener2Request.get(idleListener);

          if (request == null) {

            LOG.assertTrue(false, "There is no request for " + idleListener);

          }

          final Integer timeout = myListener2Timeout.get(idleListener);

          LOG.assertTrue(timeout != null);

          myIdleRequestsAlarm.addRequest(request, timeout.intValue(), ModalityState.NON_MODAL);

        }



        if (

          KeyEvent.KEY_PRESSED == e.getID() ||

          KeyEvent.KEY_TYPED == e.getID() ||

          MouseEvent.MOUSE_PRESSED == e.getID() ||

          MouseEvent.MOUSE_RELEASED == e.getID() ||

          MouseEvent.MOUSE_CLICKED == e.getID()

        ) {

          addIdleTimeCounterRequest();

          for (Runnable activityListener : myActivityListeners) {

            activityListener.run();

          }

        }

      }

    }



    if (myPopupManager.isPopupActive() && myPopupManager.dispatch(e)) {

      return;

    }



    if (myDispatcher != null && myDispatcher.dispatch(e)) {

      return;

    }



    if (e instanceof InputMethodEvent) {

      if (SystemInfo.isMac && myKeyEventDispatcher.isWaitingForSecondKeyStroke()) {

        return;

      }

    }



    if ((e instanceof InputEvent) && Patches.SPECIAL_WINPUT_METHOD_PROCESSING) {

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

      if (!myMouseEventDispatcher.dispatchMouseEvent((MouseEvent)e)) {

        defaultDispatchEvent(e);

      }

    }

    else {

      defaultDispatchEvent(e);

    }

  }



  private void defaultDispatchEvent(final AWTEvent e) {

    try {

      super.dispatchEvent(e);

    }

    catch (Throwable exc) {

      LOG.error("Error during dispatching of " + e, exc);

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



  public void pumpEventsForHierarchy(Component modalComponent, Condition<AWTEvent> exitCondition) {

    AWTEvent event;

    do {

      try {

        boolean eventOk = true;

        event = getNextEvent();

        if (event instanceof InputEvent) {

          final Object s = event.getSource();

          if (s instanceof Component) {

            Component c = (Component)s;

            Window modalWindow = SwingUtilities.windowForComponent(modalComponent);

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



    public MyFireIdleRequest(final Runnable runnable) {

      myRunnable = runnable;

    }



    public void run() {

      myRunnable.run();

      synchronized (myLock) {

        final Integer timeout = myListener2Timeout.get(myRunnable);

        LOG.assertTrue(timeout != null);

        myIdleRequestsAlarm.addRequest(this, timeout.intValue(), ModalityState.NON_MODAL);

      }

    }

  }



  private final class ExitSuspendModeRunnable implements Runnable {

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

}