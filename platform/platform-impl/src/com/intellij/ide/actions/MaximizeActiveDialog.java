package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.ScreenUtil;

import javax.swing.FocusManager;
import javax.swing.*;
import java.awt.*;

public class MaximizeActiveDialog extends DumbAwareAction {
  {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Window window = FocusManager.getCurrentManager().getActiveWindow();
    if (window instanceof JDialog && ((JDialog)window).isResizable()) {
      JDialog d = (JDialog)window;
      JRootPane rootPane = d.getRootPane();
      if (rootPane == null) return;
      Rectangle screenRectangle = ScreenUtil.getScreenRectangle(d);


      if (d.getBounds().equals(screenRectangle)) {
        //We have to restore normal state
        Object value = rootPane.getClientProperty("NORMAL_BOUNDS");
        if (value instanceof Rectangle) {
          Rectangle bounds = (Rectangle)value;
          ScreenUtil.fitToScreen(bounds);
          d.setBounds(bounds);
          rootPane.putClientProperty("NORMAL_BOUNDS", null);
        }
      }
      else {
        rootPane.putClientProperty("NORMAL_BOUNDS", d.getBounds());
        d.setBounds(screenRectangle);
      }
    }
  }
}
