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
public class ToggleFlattenPackagesAction extends BreakpointPanelAction {
  public ToggleFlattenPackagesAction() {
    super(DebuggerBundle.message("button.flatten.packages"));
  }

  public boolean isStateAction() {
    return true;
  }

  public void setButton(AbstractButton button) {
    super.setButton(button);
    getButton().setSelected(getPanel().getTree().isFlattenPackages());
  }

  public void actionPerformed(ActionEvent e) {
    getPanel().getTree().setFlattenPackages(getButton().isSelected());
  }

  public void update() {
    final AbstractButton button = getButton();
    final BreakpointPanel panel = getPanel();
    button.setEnabled(panel.getBreakpointCount() > 0 && panel.isTreeShowing());
    button.setSelected(panel.getTree().isFlattenPackages());
  }
}
