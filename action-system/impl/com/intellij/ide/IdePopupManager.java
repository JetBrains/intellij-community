package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.IdePopup;
import com.intellij.ui.popup.StackingPopupDispatcher;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public final class IdePopupManager implements IdeEventQueue.EventDispatcher {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.IdePopupManager");

  private IdePopup myActivePopup;

  boolean isPopupActive() {
    if (myActivePopup != null) {
      final Component component = myActivePopup.getComponent();
      if (component == null || !component.isShowing()) {
        IdePopup activePopup = myActivePopup;
        myActivePopup = null;
        if (component == null) {
          LOG.error("Popup " + activePopup + " is set up as active but not showing (component=null)");
        }
        else {
          LOG.error("Popup " + component + " is set up as active but not showing");
        }
      }
    }
    return myActivePopup != null;
  }

  public boolean dispatch(AWTEvent e) {
    LOG.assertTrue(isPopupActive());

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
    return myActivePopup instanceof StackingPopupDispatcher && ((StackingPopupDispatcher)myActivePopup).closeActivePopup();
  }
}
