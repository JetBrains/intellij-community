// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.settings.DebuggerSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class BreakpointDefaults {
  private String mySuspendPolicy = DebuggerSettings.SUSPEND_ALL;
  private boolean myIsConditionEnabled = true;

  public BreakpointDefaults() {
  }

  public BreakpointDefaults(String suspendPolicy, boolean conditionEnabled) {
    setSuspendPolicy(suspendPolicy);
    this.myIsConditionEnabled = conditionEnabled;
  }

  public @NotNull String getSuspendPolicy() {
    return mySuspendPolicy;
  }

  public void setSuspendPolicy(String suspendPolicy) {
    if (DebuggerSettings.SUSPEND_THREAD.equals(suspendPolicy) || DebuggerSettings.SUSPEND_ALL.equals(suspendPolicy)) {
      mySuspendPolicy = suspendPolicy;
    }
  }

  public boolean isConditionEnabled() {
    return myIsConditionEnabled;
  }

  public void setConditionEnabled(boolean isConditionEnabled) {
    myIsConditionEnabled = isConditionEnabled;
  }
}
