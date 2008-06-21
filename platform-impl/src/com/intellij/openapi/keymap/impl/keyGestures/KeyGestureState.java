package com.intellij.openapi.keymap.impl.keyGestures;

import com.intellij.openapi.actionSystem.KeyboardGestureAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.*;
import java.awt.event.KeyEvent;

abstract class KeyGestureState {

  final KeyboardGestureProcessor myProcessor;

  final StateContext myContext;

  public KeyGestureState(final KeyboardGestureProcessor processor) {
    myProcessor = processor;
    myContext = processor.myContext;
  }

  abstract boolean process();

  public void processDblClickTimer() {
    myProcessor.setState(myProcessor.myWaitForStart);
    myProcessor.myDblClickTimer.stop();
  }

  boolean isPureModifierEvent(int eventType) {
    final KeyEvent event = myContext.keyToProcess;
    if (event.getID() != eventType) return false;

    return event.getKeyCode() == KeyEvent.VK_CONTROL
        || event.getKeyCode() == KeyEvent.VK_ALT
        || event.getKeyCode() == KeyEvent.VK_SHIFT
        || event.getKeyCode() == KeyEvent.VK_META;
  }

  public AnActionEvent createActionEvent() {
    throw new IllegalStateException(getClass().getName());
  }

  static class WaitForStart extends KeyGestureState {

    WaitForStart(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    boolean process() {
      if (!isPureModifierEvent(KeyEvent.KEY_PRESSED)) return false;


      myProcessor.myContext.actionKey = myProcessor.myContext.keyToProcess;
      myProcessor.setState(myProcessor.myModifierPressed);
      myProcessor.myHoldTimer.start();

      return false;
    }
  }

  static class ModifierPressed extends KeyGestureState {

    ModifierPressed(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    boolean process() {
      if (isPureModifierEvent(KeyEvent.KEY_RELEASED)) {
        myProcessor.myHoldTimer.stop();
        myContext.actionKey = null;
        myProcessor.setState(myProcessor.myWaitForDblClick);

        myProcessor.myDblClickTimer.start();
        myContext.actionKey = myContext.keyToProcess;

        return false;
      } else if (isPureModifierEvent(KeyEvent.KEY_PRESSED)) {
        myContext.actionKey = myContext.keyToProcess;
        return false;
      }

      myProcessor.setState(myProcessor.myWaitForStart);
      return false;
    }
  }

  static class WaitForDblClick extends KeyGestureState {

    WaitForDblClick(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    boolean process() {
      myProcessor.myDblClickTimer.stop();

      if (isPureModifierEvent(KeyEvent.KEY_RELEASED)) return false;

      if (!isPureModifierEvent(KeyEvent.KEY_PRESSED)) {
        myProcessor.setState(myProcessor.myWaitForStart);
        return false;
      }

      myContext.actionKey = myContext.keyToProcess;
      myProcessor.setState(myProcessor.myWaitForAction);

      myContext.actionShortcut = KeyStroke.getKeyStrokeForEvent(myContext.actionKey);
      myContext.modifierType = KeyboardGestureAction.ModifierType.dblClick;

      myProcessor.executeAction();

      return true;
    }

    @Override
    public AnActionEvent createActionEvent() {
      return new GestureActionEvent.Init(myProcessor);
    }
  }

  static class WaitForAction extends KeyGestureState {

    WaitForAction(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    boolean process() {
      if (isPureModifierEvent(KeyEvent.KEY_PRESSED)) {
        myContext.actionKey = myContext.keyToProcess;
        return true;
      }

      if (myContext.keyToProcess.getID() == KeyEvent.KEY_RELEASED && myContext.keyToProcess.getKeyChar() == KeyEvent.CHAR_UNDEFINED) {
        final int pressedModifiers = myContext.keyToProcess.getKeyCode() & myContext.actionKey.getModifiersEx();
        if (pressedModifiers == 0) {
          myProcessor.setState(myProcessor.myWaitForStart);
          return true;
        }
      }

      if (myContext.keyToProcess.getID() == KeyEvent.KEY_PRESSED) {
        myContext.actionKey = myContext.keyToProcess;
        myProcessor.setState(myProcessor.myWaitForActionEnd);
        return true;
      }

      return false;
    }
  }

  static class WaitForActionEnd extends KeyGestureState {

    WaitForActionEnd(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    boolean process() {
      if (myContext.keyToProcess.getID() == KeyEvent.KEY_RELEASED) {
        if (myContext.keyToProcess.getModifiersEx() != myContext.actionKey.getModifiersEx()) {
          myProcessor.setState(myProcessor.myWaitForAction);
          return true;
        }

        myProcessor.setState(myProcessor.myWaitForAction);
        return true;
      }

      return false;
    }
  }

}