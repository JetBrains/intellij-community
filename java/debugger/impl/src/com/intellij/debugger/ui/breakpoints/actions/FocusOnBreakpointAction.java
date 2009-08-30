package com.intellij.debugger.ui.breakpoints.actions;

import com.intellij.execution.ui.actions.AbstractFocusOnAction;
import com.intellij.debugger.ui.DebuggerSessionTab;

public class FocusOnBreakpointAction extends AbstractFocusOnAction {
  public FocusOnBreakpointAction() {
    super(DebuggerSessionTab.BREAKPOINT_CONDITION);
  }
}