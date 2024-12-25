// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PositionManagerEx implements PositionManagerWithConditionEvaluation {
  public @Nullable XStackFrame createStackFrame(@NotNull StackFrameDescriptorImpl descriptor) {
    Location location = descriptor.getLocation();
    if (location != null) {
      return createStackFrame(descriptor.getFrameProxy(), (DebugProcessImpl)descriptor.getDebugProcess(), location);
    }
    return null;
  }

  public @Nullable XStackFrame createStackFrame(@NotNull StackFrameProxyImpl frame, @NotNull DebugProcessImpl debugProcess, @NotNull Location location) {
    return null;
  }
}
