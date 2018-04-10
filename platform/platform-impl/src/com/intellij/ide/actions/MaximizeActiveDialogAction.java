package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.ScreenUtil;

import javax.swing.*;
import java.awt.*;

public class MaximizeActiveDialogAction extends WindowAction {
  private static final String NORMAL_BOUNDS = "NORMAL_BOUNDS";

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (myWindow instanceof JDialog) {
      doMaximize((JDialog)myWindow);
    }
  }

  public static void doMaximize(JDialog dialog) {
    if (canBeMaximized(dialog)) {
      maximize(dialog);
    } else if (canBeNormalized(dialog)) {
      normalize(dialog);
    }
  }

  public static boolean canBeMaximized(JDialog dialog) {
    JRootPane rootPane = dialog != null && dialog.isResizable() ? dialog.getRootPane() : null;
    if (rootPane == null) return false;
    return !ScreenUtil.getScreenRectangle(dialog).equals(dialog.getBounds());
  }

  public static void maximize(JDialog dialog) {
    if (!canBeMaximized(dialog)) return;
    dialog.getRootPane().putClientProperty(NORMAL_BOUNDS, dialog.getBounds());
    dialog.setBounds(ScreenUtil.getScreenRectangle(dialog));
  }

  public static boolean canBeNormalized(JDialog dialog) {
    JRootPane rootPane = dialog != null && dialog.isResizable() ? dialog.getRootPane() : null;
    if (rootPane == null) return false;
    Rectangle screenRectangle = ScreenUtil.getScreenRectangle(dialog);
    return dialog.getBounds().equals(screenRectangle) && rootPane.getClientProperty(NORMAL_BOUNDS) instanceof Rectangle;
  }

  public static void normalize(JDialog dialog) {
    if (!canBeNormalized(dialog)) return;
    JRootPane rootPane = dialog.getRootPane();
    Object value = rootPane.getClientProperty(NORMAL_BOUNDS);
    if (value instanceof Rectangle) {
      Rectangle bounds = (Rectangle)value;
      ScreenUtil.fitToScreen(bounds);
      dialog.setBounds(bounds);
      rootPane.putClientProperty(NORMAL_BOUNDS, null);
    }
  }
}
