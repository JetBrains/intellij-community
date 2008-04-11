package com.intellij.openapi.ui.popup;

import com.intellij.openapi.components.ServiceManager;

import java.awt.*;
import java.awt.event.KeyEvent;

public abstract class StackingPopupDispatcher implements IdePopup {
  
  public abstract boolean isPopupFocused();

  public abstract void onPopupShown(JBPopup popup, boolean inStack);
  public abstract void onPopupHidden(JBPopup popup);

  public static StackingPopupDispatcher getInstance() {
    return ServiceManager.getService(StackingPopupDispatcher.class);
  }


  public abstract void hidePersistentPopups();

  public abstract void restorePersistentPopups();

  public abstract void eventDispatched(AWTEvent event);

  protected abstract boolean dispatchMouseEvent(AWTEvent event);

  protected abstract JBPopup findPopup();

  public abstract boolean dispatchKeyEvent(KeyEvent e);

  public abstract void requestFocus();

  public abstract boolean close();

  public abstract boolean closeActivePopup();
}
