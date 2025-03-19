// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SteppingListener {
  companion object {
    private val EP_NAME = ExtensionPointName<SteppingListener>("com.intellij.debugger.steppingListener")

    @JvmStatic
    val extensions: List<SteppingListener> get() = EP_NAME.extensionList
  }

  fun beforeSteppingStarted(suspendContext: SuspendContextImpl, steppingAction: SteppingAction) { }

  /**
   * Called before resume, not called for stepping commands.
   */
  fun beforeResume(suspendContext: SuspendContextImpl) {}
}
