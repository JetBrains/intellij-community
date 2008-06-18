package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
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
  private ActionProcessor myActionProcessor = new MyActionProcessor();

  public KeyboardGestureProcessor(final IdeKeyEventDispatcher dispatcher) {
    myDispatcher = dispatcher;
  }

  public boolean process() {
    myCurrentEvent.key = myDispatcher.getContext().getInputEvent();
    myCurrentEvent.isModal = myDispatcher.getContext().isModalContext();
    myCurrentEvent.dataContext = myDispatcher.getContext().getDataContext();

    return myState.process();
  }

  public boolean processInitState() {
    myCurrentEvent.focusOwner = myDispatcher.getContext().getFocusOwner();
    return process();
  }

  private void notifyOnState(final StateProcessor processor) {
    final KeyProcessorContext context =
        myDispatcher.updateCurrentContext(myCurrentEvent.focusOwner, getCurrentShortcut(), myCurrentEvent.isModal);


    myDispatcher.processAction(myCurrentEvent.key, myActionProcessor);


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
      myContext.type = KeyboardGestureAction.ModifierType.dblClick;

      notifyOnState(StateProcessor.INIT);

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
    public KeyboardGestureAction.ModifierType type;

    public void setActiveKey(final KeyEvent key) {
      activeKey = key;
    }
  }

  public static class StateProcessor {

    static StateProcessor INIT = new StateProcessor();
    static StateProcessor ACTION = new StateProcessor();
    static StateProcessor FINISH = new StateProcessor();

  }

  private static class MyActionProcessor implements ActionProcessor {
    public AnActionEvent createEvent(final InputEvent inputEvent, final DataContext context, final String place, final Presentation presentation,
                                     final ActionManager manager) {
      return new GestureKeyEvent(inputEvent, context, place, presentation, manager, 0);
    }

    public void onUpdatePassed(final InputEvent inputEvent, final AnAction action, final AnActionEvent actionEvent) {
    }

    public void performAction(final InputEvent e, final AnAction action, final AnActionEvent actionEvent) {
    }
  }

  public static class GestureKeyEvent extends AnActionEvent {
    public GestureKeyEvent(final InputEvent inputEvent, @NotNull final DataContext dataContext, @NotNull @NonNls final String place, @NotNull final Presentation presentation,
                           final ActionManager actionManager,
                           final int modifiers) {
      super(inputEvent, dataContext, place, presentation, actionManager, modifiers);
    }

    @Override
    public void accept(final AnActionEventVisitor visitor) {
      super.accept(visitor);
    }
  }
}