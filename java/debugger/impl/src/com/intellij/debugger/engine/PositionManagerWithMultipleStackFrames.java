// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PositionManagerWithMultipleStackFrames extends PositionManagerWithConditionEvaluation {
  @Nullable
  default List<XStackFrame> createStackFrames(@NotNull StackFrameDescriptorImpl descriptor) {
    return null;
  }
}
