package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.ScreenUtil;

import javax.swing.*;
import java.awt.*;

public class MaximizeActiveDialogAction extends WindowAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    if (myWindow instanceof JDialog) {
      doMaximize((JDialog)myWindow);
    }
  }

  public static void doMaximize(JDialog dialog) {
    JRootPane rootPane = dialog != null ? dialog.getRootPane() : null;
    if (rootPane == null) return;
    Rectangle screenRectangle = ScreenUtil.getScreenRectangle(dialog);


    if (dialog.getBounds().equals(screenRectangle)) {
      //We have to restore normal state
      Object value = rootPane.getClientProperty("NORMAL_BOUNDS");
      if (value instanceof Rectangle) {
        Rectangle bounds = (Rectangle)value;
        ScreenUtil.fitToScreen(bounds);
        dialog.setBounds(bounds);
        rootPane.putClientProperty("NORMAL_BOUNDS", null);
      }
    }
    else {
      rootPane.putClientProperty("NORMAL_BOUNDS", dialog.getBounds());
      dialog.setBounds(screenRectangle);
    }
  }
}
