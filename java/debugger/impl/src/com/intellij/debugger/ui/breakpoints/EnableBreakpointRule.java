/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: May 10, 2005
 */
public class EnableBreakpointRule {
  private final BreakpointManager myManager;
  private final boolean myLeaveEnabled;
  private final Breakpoint myMasterBreakpoint;
  private final Breakpoint mySlaveBreakpoint;

  public EnableBreakpointRule(@NotNull BreakpointManager manager, @NotNull Breakpoint masterBreakpoint, @NotNull Breakpoint slaveBreakpoint) {
    this(manager,masterBreakpoint, slaveBreakpoint, false);
  }

  public EnableBreakpointRule(@NotNull BreakpointManager manager, @NotNull Breakpoint masterBreakpoint, @NotNull Breakpoint slaveBreakpoint, boolean leaveEnabled) {
    myMasterBreakpoint = masterBreakpoint;
    mySlaveBreakpoint = slaveBreakpoint;
    myManager = manager;
    myLeaveEnabled = leaveEnabled;
  }

  public Breakpoint getMasterBreakpoint() {
    return myMasterBreakpoint;
  }

  public Breakpoint getSlaveBreakpoint() {
    return mySlaveBreakpoint;
  }

  public boolean isLeaveEnabled() {
    return myLeaveEnabled;
  }

  public void init() {
    myManager.setBreakpointEnabled(getSlaveBreakpoint(), false);
  }

  public void dispose() {
    myManager.setBreakpointEnabled(getSlaveBreakpoint(), true);
  }

  public void processBreakpointHit(Breakpoint breakpointHit) {
    if (getMasterBreakpoint().equals(breakpointHit)) {
      myManager.setBreakpointEnabled(getSlaveBreakpoint(), true);
    }
    else if (getSlaveBreakpoint().equals(breakpointHit)){
      if (!myLeaveEnabled) {
        myManager.setBreakpointEnabled(getSlaveBreakpoint(), false);
      }
    }
  }

}
