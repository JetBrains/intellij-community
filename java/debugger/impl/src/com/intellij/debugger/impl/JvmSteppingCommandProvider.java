// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.MethodFilter;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to change the behavior of the standard java debugger stepping
 */
public abstract class JvmSteppingCommandProvider {
  public static final ExtensionPointName<JvmSteppingCommandProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.debugger.jvmSteppingCommandProvider");

  /**
   * @return null if can not handle
   * @see DebugProcessImpl#createStepIntoCommand(SuspendContextImpl, boolean, MethodFilter, int)
   */
  public DebugProcessImpl.ResumeCommand getStepIntoCommand(SuspendContextImpl suspendContext,
                                                           boolean ignoreFilters,
                                                           final MethodFilter smartStepFilter,
                                                           int stepSize) {
    return null;
  }

  /**
   * @return null if can not handle
   * @see DebugProcessImpl#createStepOutCommand(SuspendContextImpl, int)
   */
  public DebugProcessImpl.ResumeCommand getStepOutCommand(SuspendContextImpl suspendContext, int stepSize) {
    return null;
  }

  /**
   * @return null if can not handle
   * @see DebugProcessImpl#createStepOverCommand(SuspendContextImpl, boolean, MethodFilter, int)
   */
  public DebugProcessImpl.ResumeCommand getStepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints, int stepSize) {
    return null;
  }

  /**
   * @return null iff cannot handle
   * @see DebugProcessImpl#createRunToCursorCommand(SuspendContextImpl, XSourcePosition, boolean)
   */
  public DebugProcessImpl.@Nullable ResumeCommand getRunToCursorCommand(SuspendContextImpl suspendContext,
                                                                        @NotNull XSourcePosition position,
                                                                        boolean ignoreBreakpoints) {
    return null;
  }
}
