// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.impl.XSteppingSuspendContext

internal class JavaSteppingSuspendContext(
  private val debugProcessImpl: DebugProcessImpl,
  private val javaExecutionStack: JavaExecutionStack
) : XSteppingSuspendContext() {

  override fun getActiveExecutionStack(): XExecutionStack? {
    return javaExecutionStack
  }

  override fun computeExecutionStacks(container: XExecutionStackContainer) {
    debugProcessImpl.suspendManager.pausedContexts.firstOrNull()?.computeExecutionStacks(container)
  }
}