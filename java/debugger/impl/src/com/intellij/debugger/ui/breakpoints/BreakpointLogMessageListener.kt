// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.memory.utils.StackFrameItem
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

/**
 * Listener for breakpoint messages: evaluate and log, stack trace, "breakpoint hit", etc.
 */
@ApiStatus.Internal
interface BreakpointLogMessageListener : EventListener {
  /** Note that it's called only for non-instrumented logging breakpoints. */
  fun beforeLoggingBreakpoint(context: SuspendContextImpl) {
  }

  fun onLogMessage(breakpoint: Breakpoint<*>, message: String, debugProcess: DebugProcessImpl, stack: List<StackFrameItem?>?) {
  }
}
