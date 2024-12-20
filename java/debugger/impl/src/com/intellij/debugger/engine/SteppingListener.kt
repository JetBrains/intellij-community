// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SteppingListener {
  companion object {
    private val EP_NAME = ExtensionPointName<SteppingListener>("com.intellij.debugger.steppingListener")

    @JvmStatic
    fun notifySteppingStarted(suspendContext: SuspendContextImpl, steppingAction: SteppingAction) {
      DebuggerUtilsImpl.forEachSafe(EP_NAME) {
        it.beforeSteppingStarted(suspendContext, steppingAction)
      }
    }
  }

  fun beforeSteppingStarted(suspendContext: SuspendContextImpl, steppingAction: SteppingAction) { }
}
