// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.RequestHint;
import com.intellij.debugger.requests.Requestor;

public interface SteppingBreakpoint extends Requestor {
  boolean isRestoreBreakpoints();

  void setRequestHint(RequestHint hint);

  void setSuspendPolicy(String s);

  void createRequest(DebugProcessImpl process);
}
