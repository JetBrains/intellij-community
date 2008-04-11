/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui.popup;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.StackingPopupDispatcher;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Stack;

public class StackingPopupDispatcherImpl extends StackingPopupDispatcher implements AWTEventListener, KeyEventDispatcher {

  private Stack<JBPopupImpl> myStack = new Stack<JBPopupImpl>();
  private WeakList<JBPopup> myPersistentPopups = new WeakList<JBPopup>();

  private WeakList<JBPopup> myAllPopups = new WeakList<JBPopup>();


  private StackingPopupDispatcherImpl() {
  }

  public void onPopupShown(JBPopup popup, boolean inStack) {
    if (inStack) {
      myStack.push((JBPopupImpl)popup);
      if (ApplicationManager.getApplication() != null) {
        IdeEventQueue.getInstance().getPopupManager().setActivePopup(getInstance());
      }
    } else if (popup.isPersistent()) {
      myPersistentPopups.add(popup);
    }

    myAllPopups.add(popup);
  }

  public void onPopupHidden(JBPopup popup) {
    boolean wasInStack = myStack.remove(popup);
    myPersistentPopups.remove(popup);

    if (wasInStack && myStack.isEmpty()) {
      if (ApplicationManager.getApplication() != null) {
        IdeEventQueue.getInstance().getPopupManager().resetActivePopup();
      }
    }

    myAllPopups.remove(popup);
  }

  public void hidePersistentPopups() {
    final WeakList<JBPopup> list = myPersistentPopups;
    for (JBPopup each : list) {
      each.setUiVisible(false);
    }
  }

  public void restorePersistentPopups() {
    final WeakList<JBPopup> list = myPersistentPopups;
    for (JBPopup each : list) {
      each.setUiVisible(true);
    }
  }

  public void eventDispatched(AWTEvent event) {
    dispatchMouseEvent(event);
  }

  protected boolean dispatchMouseEvent(AWTEvent event) {
    if (event.getID() != MouseEvent.MOUSE_PRESSED) {
      return false;
    }

    if (myStack.isEmpty()) {
      return false;
    }

    JBPopupImpl popup = findPopup();

    final MouseEvent mouseEvent = ((MouseEvent) event);

    Point point = (Point) mouseEvent.getPoint().clone();
    SwingUtilities.convertPointToScreen(point, mouseEvent.getComponent());

    while (true) {
      if (popup != null && !popup.isDisposed()) {
        final Component content = popup.getContent();
        if (!content.isShowing()) {
          popup.cancel();
          return false;
        }

        final Rectangle bounds = new Rectangle(content.getLocationOnScreen(), content.getSize());
        if (bounds.contains(point) || !popup.isCancelOnClickOutside()) {
          return false;
        }

        if (!popup.canClose()){
          return false;
        }
        popup.cancel();
      }

      if (myStack.isEmpty()) {
        return false;
      }

      popup = myStack.peek();
      if (popup == null || popup.isDisposed()) {
        myStack.pop();
      }
    }
  }

  protected JBPopupImpl findPopup() {
    while(true) {
      if (myStack.isEmpty()) break;
      final JBPopupImpl each = myStack.peek();
      if (each == null || each.isDisposed()) {
        myStack.pop();
      } else {
        return each;
      }
    }

    return null;
  }

  public boolean dispatchKeyEvent(final KeyEvent e) {
    if (!isPopupFocused()) return false;

    JBPopup popup = getFocusedPopup();
    if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0) {
      if (popup.isCancelKeyEnabled()) {
        popup.cancel();
        return true;
      }
    }
    return false;
  }


  @Nullable
  public Component getComponent() {
    return myStack.size() > 0 ?myStack.peek().getContent() : null;
  }

  public boolean dispatch(AWTEvent event) {
   if (event instanceof KeyEvent) {
      return dispatchKeyEvent(((KeyEvent) event));
   } else if (event instanceof MouseEvent) {
     return dispatchMouseEvent(event);
   } else {
     return false;
   }
  }

  public void requestFocus() {
    if (myStack.isEmpty()) return;

    final JBPopupImpl popup = myStack.peek();
    popup.requestFocus();

  }

  public boolean close() {
    return closeActivePopup();
  }

  public boolean closeActivePopup() {
    if (myStack.isEmpty()) return false;

    final JBPopupImpl popup = myStack.pop();
    if (popup != null && popup.isVisible()){
      popup.cancel();
      return true;
    }
    return false;
  }

  public boolean isPopupFocused() {
    return getFocusedPopup() != null;
  }

  private JBPopup getFocusedPopup() {
    for (JBPopup each : myAllPopups) {
      if (each != null && each.isFocused()) return each;
    }
    return null;
  }
}
