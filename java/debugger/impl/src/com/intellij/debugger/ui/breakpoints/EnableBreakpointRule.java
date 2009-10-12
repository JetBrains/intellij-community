/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
