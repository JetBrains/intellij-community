package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.popup.IdePopup;
import com.intellij.openapi.wm.IdeFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;

public final class IdePopupManager implements IdeEventQueue.EventDispatcher {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.IdePopupManager");

  private IdePopup myActivePopup;

  boolean isPopupActive() {
    if (myActivePopup != null) {
      final Component component = myActivePopup.getComponent();
      if (component == null || !component.isShowing()) {
        myActivePopup = null;
      }
    }
    return myActivePopup != null;
  }

  public boolean dispatch(AWTEvent e) {
    LOG.assertTrue(isPopupActive());

    if (isPopupActive()) {
      if (e.getID() == WindowEvent.WINDOW_LOST_FOCUS) {
        final WindowEvent we = (WindowEvent)e;
        if (we.getOppositeWindow() == null) {
          Window window = we.getWindow();
          if (window instanceof IdeFrame || window instanceof DialogWrapperDialog) {
            closeActivePopup();
          }
        }
      }
    }


    if (e instanceof KeyEvent || e instanceof MouseEvent) {
      return myActivePopup.dispatch(e);
    }

    return false;
  }

  public void setActivePopup(IdePopup popup) {
    myActivePopup = popup;
  }

  public void resetActivePopup() {
    myActivePopup = null;
  }

  public boolean closeActivePopup(){
    if (myActivePopup != null) {
      return myActivePopup.close();
    }

    return false;
  }

  public void processWindowGainedFocus(final Window focusedWindow) {
    if (!isPopupActive()) return;
    final Component c = myActivePopup.getComponent();
    if (SwingUtilities.isDescendingFrom(c, focusedWindow)) {
      myActivePopup.requestFocus();
    }
  }
}
