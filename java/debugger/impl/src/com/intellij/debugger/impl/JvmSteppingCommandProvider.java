// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.MethodFilter;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.openapi.extensions.ExtensionPointName;

public abstract class JvmSteppingCommandProvider {
  public static final ExtensionPointName<JvmSteppingCommandProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.debugger.jvmSteppingCommandProvider");

  /**
   * @return null if can not handle
   */
  public DebugProcessImpl.ResumeCommand getStepIntoCommand(SuspendContextImpl suspendContext,
                                                           boolean ignoreFilters,
                                                           final MethodFilter smartStepFilter,
                                                           int stepSize) {
    return null;
  }

  /**
   * @return null if can not handle
   */
  public DebugProcessImpl.ResumeCommand getStepOutCommand(SuspendContextImpl suspendContext, int stepSize) {
    return null;
  }

  /**
   * @return null if can not handle
   */
  public DebugProcessImpl.ResumeCommand getStepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints, int stepSize) {
    return null;
  }
}
