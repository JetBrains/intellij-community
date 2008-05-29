package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.IdePopupEventDispatcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.concurrent.CopyOnWriteArrayList;

public final class IdePopupManager implements IdeEventQueue.EventDispatcher {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.IdePopupManager");

  private CopyOnWriteArrayList<IdePopupEventDispatcher> myDispatchStack = new CopyOnWriteArrayList<IdePopupEventDispatcher>();

  boolean isPopupActive() {
    for (IdePopupEventDispatcher each : myDispatchStack) {
      if (each.getComponent() == null || !each.getComponent().isShowing()) {
        myDispatchStack.remove(each);
      }
    }

    return myDispatchStack.size() > 0;
  }

  public boolean dispatch(final AWTEvent e) {
    LOG.assertTrue(isPopupActive());

    if (e.getID() == WindowEvent.WINDOW_LOST_FOCUS) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (!isPopupActive()) return;

          Window focused = ((WindowEvent)e).getOppositeWindow();
          if (focused == null) {
            focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
          }

          if (focused == null) {
            closeAllPopups();
          }
        }
      });
    }

    if (e instanceof KeyEvent || e instanceof MouseEvent) {
      for (int i = myDispatchStack.size() - 1; (i >=0 && i < myDispatchStack.size()); i--) {
        final boolean dispatched = myDispatchStack.get(i).dispatch(e);
        if (dispatched) return true;
      }
    }

    return false;
  }

  public void push(IdePopupEventDispatcher dispatcher) {
    if (!myDispatchStack.contains(dispatcher)) {
      myDispatchStack.add(dispatcher);
    }
  }

  public void remove(IdePopupEventDispatcher dispatcher) {
    myDispatchStack.remove(dispatcher);
  }

  public boolean closeAllPopups(){
    if (myDispatchStack.size() == 0) return false;

    boolean closed = true;
    for (IdePopupEventDispatcher each : myDispatchStack) {
      closed &= each.close();
    }

    return closed;
  }
}
