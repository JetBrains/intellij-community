/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.ui.breakpoints.AnyExceptionBreakpoint;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.BreakpointPanel;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2005
 */
public class RemoveAction extends BreakpointPanelAction {
  private final Project myProject;

  public RemoveAction(final Project project) {
    super(DebuggerBundle.message("button.remove"));
    myProject = project;
  }

  public void setButton(AbstractButton button) {
    super.setButton(button);
  }

  public void setPanel(BreakpointPanel panel) {
    super.setPanel(panel);
    getPanel().getTable().registerKeyboardAction(
      this, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    );
    getPanel().getTree().registerKeyboardAction(
      this, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    );
  }

  public void actionPerformed(ActionEvent e) {
    Breakpoint[] breakpoints = getPanel().getSelectedBreakpoints();
    if (breakpoints != null) {
      for (Breakpoint breakpoint1 : breakpoints) {
        if (breakpoint1 instanceof AnyExceptionBreakpoint) {
          return;
        }
      }
      getPanel().removeSelectedBreakpoints();
      BreakpointManager manager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
      for (final Breakpoint breakpoint : breakpoints) {
        getPanel().getTree().removeBreakpoint(breakpoint);
        manager.removeBreakpoint(breakpoint);
      }
    }
  }

  public void update() {
    getButton().setEnabled(getPanel().getSelectedBreakpoints().length > 0);
  }
}
