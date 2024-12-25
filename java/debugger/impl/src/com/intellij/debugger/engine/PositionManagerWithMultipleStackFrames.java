// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PositionManagerWithMultipleStackFrames extends PositionManagerWithConditionEvaluation {
  /**
   * Allows to replace a jvm frame with one or several frames, or skip a frame
   * @return a list of frames to replace the original frame with, or null to use the default mapping
   */
  default @Nullable List<XStackFrame> createStackFrames(@NotNull StackFrameDescriptorImpl descriptor) {
    return null;
  }
}
