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
  private final Breakpoint myMasterBreakpoint;
  private final Breakpoint mySlaveBreakpoint;

  public EnableBreakpointRule(@NotNull BreakpointManager manager, @NotNull Breakpoint masterBreakpoint, @NotNull Breakpoint slaveBreakpoint) {
    myMasterBreakpoint = masterBreakpoint;
    mySlaveBreakpoint = slaveBreakpoint;
    myManager = manager;
  }

  public Breakpoint getMasterBreakpoint() {
    return myMasterBreakpoint;
  }

  public Breakpoint getSlaveBreakpoint() {
    return mySlaveBreakpoint;
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
      myManager.setBreakpointEnabled(getSlaveBreakpoint(), false);
    }
  }

}
