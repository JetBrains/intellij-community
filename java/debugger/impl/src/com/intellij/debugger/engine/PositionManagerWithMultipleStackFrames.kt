// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.impl.runBlockingAssertNotInReadAction
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.xdebugger.frame.XStackFrame

interface PositionManagerWithMultipleStackFrames : PositionManagerWithConditionEvaluation {
  @Deprecated("Use createStackFramesAsync instead")
  fun createStackFrames(descriptor: StackFrameDescriptorImpl): List<XStackFrame>? {
    return runBlockingAssertNotInReadAction { createStackFramesAsync(descriptor) }
  }

  /**
   * Allows to replace a jvm frame with one or several frames, or skip a frame
   * @return a list of frames to replace the original frame with, or null to use the default mapping
   */
  suspend fun createStackFramesAsync(descriptor: StackFrameDescriptorImpl): List<XStackFrame>? {
    return null
  }
}
