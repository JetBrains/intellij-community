/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints.actions;

import com.intellij.debugger.ui.breakpoints.BreakpointPanel;
import com.intellij.debugger.DebuggerBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2005
 */
public class SwitchViewAction extends BreakpointPanelAction {
  public SwitchViewAction() {
    super(DebuggerBundle.message("button.switch.view"));
  }

  public void setButton(AbstractButton button) {
    super.setButton(button);
  }

  public void actionPerformed(ActionEvent e) {
    getPanel().switchViews();
  }


  public void update() {
    final AbstractButton button = getButton();
    final BreakpointPanel panel = getPanel();
    button.setText(panel.isTreeShowing()? DebuggerBundle.message("button.list.view") : DebuggerBundle.message("button.tree.view"));
    button.setEnabled(panel.getBreakpointCount() > 0);
  }
}
