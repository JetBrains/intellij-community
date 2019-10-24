// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PositionManagerEx implements PositionManager {
  @Nullable
  public XStackFrame createStackFrame(@NotNull StackFrameDescriptorImpl descriptor) {
    Location location = descriptor.getLocation();
    if (location != null) {
      return createStackFrame(descriptor.getFrameProxy(), (DebugProcessImpl)descriptor.getDebugProcess(), location);
    }
    return null;
  }

  @Nullable
  public XStackFrame createStackFrame(@NotNull StackFrameProxyImpl frame, @NotNull DebugProcessImpl debugProcess, @NotNull Location location) {
    return null;
  }

  public abstract ThreeState evaluateCondition(@NotNull EvaluationContext context,
                                               @NotNull StackFrameProxyImpl frame,
                                               @NotNull Location location,
                                               @NotNull String expression);
}
