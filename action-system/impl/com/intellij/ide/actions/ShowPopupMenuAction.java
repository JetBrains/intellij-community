package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Vladimir Kondratyev
 */
public class ShowPopupMenuAction extends AnAction {
  public ShowPopupMenuAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final RelativePoint relPoint = getPopupLocation(e.getDataContext());

    Component focusOwner = relPoint.getComponent();
    Point popupMenuPoint = relPoint.getPoint();

    focusOwner.dispatchEvent(
      new MouseEvent(
        focusOwner,
        MouseEvent.MOUSE_PRESSED,
        System.currentTimeMillis(), 0,
        popupMenuPoint.x,
        popupMenuPoint.y,
        1,
        true
      )
    );
  }

  private RelativePoint getPopupLocation(final DataContext dataContext) {
    return JBPopupFactory.getInstance().guessBestPopupLocation(dataContext);
  }

  public void update(AnActionEvent e) {
    KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    e.getPresentation().setEnabled(focusManager.getFocusOwner() instanceof JComponent);
  }
}