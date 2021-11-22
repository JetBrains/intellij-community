// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PositionManagerWithMultipleStackFrames extends PositionManagerWithConditionEvaluation {
  @Nullable
  default List<XStackFrame> createStackFrames(@NotNull StackFrameProxyImpl frame,
                                              @NotNull DebugProcessImpl debugProcess,
                                              @NotNull Location location) {
    return null;
  }
}
