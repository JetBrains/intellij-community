// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints

import com.intellij.debugger.engine.DebugProcessImpl
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

/**
 * Listener for breakpoint messages: evaluate and log, stack trace, "breakpoint hit", etc.
 */
@ApiStatus.Internal
fun interface BreakpointLogMessageListener : EventListener {
  fun onLogMessage(breakpoint: Breakpoint<*>, message: String, debugProcess: DebugProcessImpl)
}
