// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.MethodFilter;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Allows to change the behavior of the standard java debugger stepping
 */
public abstract class JvmSteppingCommandProvider {
  public static final ExtensionPointName<JvmSteppingCommandProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.debugger.jvmSteppingCommandProvider");

  /**
   * @see DebugProcessImpl#createStepIntoCommand(SuspendContextImpl, boolean, MethodFilter, int)
   * @return null if can not handle
   */
  public DebugProcessImpl.ResumeCommand getStepIntoCommand(SuspendContextImpl suspendContext,
                                                           boolean ignoreFilters,
                                                           final MethodFilter smartStepFilter,
                                                           int stepSize) {
    return null;
  }

  /**
   * @see DebugProcessImpl#createStepOutCommand(SuspendContextImpl, int)
   * @return null if can not handle
   */
  public DebugProcessImpl.ResumeCommand getStepOutCommand(SuspendContextImpl suspendContext, int stepSize) {
    return null;
  }

  /**
   * @see DebugProcessImpl#createStepOverCommand(SuspendContextImpl, boolean, MethodFilter, int)
   * @return null if can not handle
   */
  public DebugProcessImpl.ResumeCommand getStepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints, int stepSize) {
    return null;
  }
}
