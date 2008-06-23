package com.intellij.openapi.keymap.impl.keyGestures;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.impl.ActionProcessor;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

public class KeyboardGestureProcessor {

  IdeKeyEventDispatcher myDispatcher;

  StateContext myContext = new StateContext();

  final KeyGestureState myWaitForStart = new KeyGestureState.WaitForStart(this);
  final KeyGestureState myModifierPressed = new KeyGestureState.ModifierPressed(this);
  final KeyGestureState myWaitForDblClick = new KeyGestureState.WaitForDblClick(this);
  final KeyGestureState myWaitForAction = new KeyGestureState.WaitForAction(this);
  final KeyGestureState myWaitForActionEnd = new KeyGestureState.WaitForActionEnd(this);
  final KeyGestureState myFinish = new KeyGestureState.ProcessFinish(this);

  KeyGestureState myState = myWaitForStart;


  Timer myHoldTimer = new Timer(1200, new ActionListener() {
    public void actionPerformed(final ActionEvent e) {
    }
  });

  Timer myDblClickTimer = new Timer(500, new ActionListener() {
    public void actionPerformed(final ActionEvent e) {
      myState.processDblClickTimer();
    }
  });
  private ActionProcessor myActionProcessor = new MyActionProcessor();

  public KeyboardGestureProcessor(final IdeKeyEventDispatcher dispatcher) {
    myDispatcher = dispatcher;
  }

  public boolean process() {
    myContext.keyToProcess = myDispatcher.getContext().getInputEvent();
    myContext.isModal = myDispatcher.getContext().isModalContext();
    myContext.dataContext = myDispatcher.getContext().getDataContext();

    return myState.process();
  }

  public boolean processInitState() {
    myContext.focusOwner = myDispatcher.getContext().getFocusOwner();
    return process();
  }

  void executeAction() {
    myDispatcher.updateCurrentContext(myContext.focusOwner, getCurrentShortcut(), myContext.isModal);
    myDispatcher.processAction(myContext.keyToProcess, myActionProcessor);
  }

  private Shortcut getCurrentShortcut() {
    return KeyboardModifierGestureShortuct.newInstance(myContext.modifierType, myContext.actionShortcut);
  }

  void setState(KeyGestureState state) {
    final boolean isGestureProcessingState = myDispatcher.getState() == IdeKeyEventDispatcher.STATE_KEY_GESTURE_PROCESSOR;
    if (state == myWaitForStart) {
      myContext.actionKey = null;
      if (isGestureProcessingState) {
        myDispatcher.setState(IdeKeyEventDispatcher.STATE_INIT);
      }
    } else if (state == myWaitForAction) {
      myDispatcher.setState(IdeKeyEventDispatcher.STATE_KEY_GESTURE_PROCESSOR);
    }
    myState = state;
  }

  public ActionManager getActionManager() {
    return ActionManager.getInstance();
  }


  private class MyActionProcessor implements ActionProcessor {
    public AnActionEvent createEvent(final InputEvent inputEvent, final DataContext context, final String place, final Presentation presentation,
                                     final ActionManager manager) {
      myContext.actionPresentation = presentation;
      myContext.actionPlace = place;
      return myState.createActionEvent();
    }

    public void onUpdatePassed(final InputEvent inputEvent, final AnAction action, final AnActionEvent actionEvent) {
    }

    public void performAction(final InputEvent e, final AnAction action, final AnActionEvent actionEvent) {
      final boolean isGestureAction = action instanceof KeyboardGestureAction;
      actionEvent.accept(new AnActionEventVisitor() {
        @Override
        public void visitGestureInitEvent(final AnActionEvent anActionEvent) {
          if (isGestureAction) {
            execute(anActionEvent, action, e);
          }
        }

        @Override
        public void visitGesturePerformedEvent(final AnActionEvent anActionEvent) {
          execute(anActionEvent, action, e);
        }

        @Override
        public void visitGestureFinishEvent(final AnActionEvent anActionEvent) {
          if (isGestureAction) {
            execute(anActionEvent, action, e);
          }
        }
      });
    }

    private void execute(final AnActionEvent anActionEvent, final AnAction action, final InputEvent e) {
      action.actionPerformed(anActionEvent);
      e.consume();
    }
  }

}