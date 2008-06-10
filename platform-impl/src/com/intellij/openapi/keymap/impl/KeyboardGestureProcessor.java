package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortuct;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.KeyboardGestureAction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public class KeyboardGestureProcessor {

  private IdeKeyEventDispatcher myDispatcher;

  private State myWaitForStart = new WaitForStart();
  private State myModifierPressed = new ModifierPressed();
  private State myWaitForDblClick = new WaitForDblClick();
  private State myWaitForAction = new WaitForAction();
  private State myWaitForActionEnd = new WaitForActionEnd();

  private State myState = myWaitForStart;

  private CurrentEvent myCurrentEvent = new CurrentEvent();
  private StateContext myContext = new StateContext();

  private Timer myHoldTimer = new Timer(1200, new ActionListener() {
    public void actionPerformed(final ActionEvent e) {
    }
  });

  private Timer myDblClickTimer = new Timer(500, new ActionListener() {
    public void actionPerformed(final ActionEvent e) {
      myState.processDblClickTimer();
    }
  });

  public KeyboardGestureProcessor(final IdeKeyEventDispatcher dispatcher) {
    myDispatcher = dispatcher;
  }

  public boolean process(KeyEvent e, boolean isModalContext, DataContext dataContext) {
    myCurrentEvent.key = e;
    myCurrentEvent.isModal = isModalContext;
    myCurrentEvent.dataContext = dataContext;

    return myState.process();
  }

  public boolean processInitState(final Component focusOwner, final KeyEvent e, final boolean modalContext, final DataContext dataContext) {
    myCurrentEvent.focusOwner = focusOwner;
    return process(e, modalContext, dataContext);
  }

  private void notifyOnState(final KeyboardGestureAction.State state) {
    final boolean actions = myDispatcher.fillActionsList(myCurrentEvent.focusOwner, getCurrentShortcut(), myCurrentEvent.isModal);

  }

  private Shortcut getCurrentShortcut() {
    return KeyboardModifierGestureShortuct.newInstance(myContext.type, myContext.actionShortcut);
  }

  private void setState(State state) {
    myState = state;
    if (myState == myWaitForStart) {
      myContext.setActiveKey(null);
    }
  }

  abstract class State {
    abstract boolean process();

    public void processDblClickTimer() {
      setState(myWaitForStart);
      myDblClickTimer.stop();
    }

    boolean isPureModifierEvent(int eventType) {
      final KeyEvent event = myCurrentEvent.key;
      if (event.getID() != eventType) return false;

      return event.getKeyCode() == KeyEvent.VK_CONTROL
          || event.getKeyCode() == KeyEvent.VK_ALT
          || event.getKeyCode() == KeyEvent.VK_SHIFT
          || event.getKeyCode() == KeyEvent.VK_META;
    }

  }

  class WaitForStart extends State {

    boolean process() {
      if (!isPureModifierEvent(KeyEvent.KEY_PRESSED)) return false;


      myContext.setActiveKey(myCurrentEvent.key);
      setState(myModifierPressed);
      myHoldTimer.start();

      return false;
    }
  }

  class ModifierPressed extends State {

    boolean process() {
      if (isPureModifierEvent(KeyEvent.KEY_RELEASED)) {
        myHoldTimer.stop();
        myContext.setActiveKey(null);
        setState(myWaitForDblClick);

        myDblClickTimer.start();
        myContext.setActiveKey(myCurrentEvent.key);

        return false;
      } else if (isPureModifierEvent(KeyEvent.KEY_PRESSED)) {
        myContext.setActiveKey(myCurrentEvent.key);
        return false;
      }

      setState(myWaitForStart);
      return false;
    }
  }

  class WaitForDblClick extends State {
    boolean process() {
      myDblClickTimer.stop();

      if (isPureModifierEvent(KeyEvent.KEY_RELEASED)) return false;

      if (!isPureModifierEvent(KeyEvent.KEY_PRESSED)) {
        setState(myWaitForStart);
        return false;
      }

      myContext.setActiveKey(myCurrentEvent.key);
      setState(myWaitForAction);

      myContext.actionShortcut = KeyStroke.getKeyStrokeForEvent(myContext.activeKey);
      myContext.type = KeyboardGestureAction.Type.dblClick;

      notifyOnState(KeyboardGestureAction.State.init);

      return true;
    }
  }

  class WaitForAction extends State {
    boolean process() {
      if (isPureModifierEvent(KeyEvent.KEY_PRESSED)) {
        myContext.setActiveKey(myCurrentEvent.key);
        return true;
      }

      if (myCurrentEvent.key.getID() == KeyEvent.KEY_RELEASED && myCurrentEvent.key.getKeyChar() == KeyEvent.CHAR_UNDEFINED) {
        final int pressedModifiers = myCurrentEvent.key.getKeyCode() & myContext.activeKey.getModifiersEx();
        if (pressedModifiers == 0) {
          setState(myWaitForStart);
          return true;
        }
      }

      if (myCurrentEvent.key.getID() == KeyEvent.KEY_PRESSED) {
        myContext.setActiveKey(myCurrentEvent.key);
        setState(myWaitForActionEnd);
        return true;
      }

      return false;
    }
  }

  class WaitForActionEnd extends State {
    boolean process() {
      if (myCurrentEvent.key.getID() == KeyEvent.KEY_RELEASED) {
        if (myCurrentEvent.key.getModifiersEx() != myContext.activeKey.getModifiersEx()) {
          setState(myWaitForAction);
          return true;
        }

        setState(myWaitForAction);
        return true;
      }

      return false;
    }
  }

  private class CurrentEvent {
    Component focusOwner;
    KeyEvent key;
    boolean isModal;
    DataContext dataContext;
  }

  private class StateContext {
    private KeyEvent activeKey;
    long intiatorTimestamp;
    public KeyStroke actionShortcut;
    public KeyboardGestureAction.Type type;

    public void setActiveKey(final KeyEvent key) {
      activeKey = key;
    }
  }
}