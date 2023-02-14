/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

  @NotNull
  public String getSuspendPolicy() {
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
