// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service
class WindowFocusFrontendService {
  private var isFrontendWindowFocused: Boolean = false

  /**
   * Performs the given [action] while marking all dialogs created during its execution
   * with `isFrontendWindowFocused`. When such a dialog is luxed to the frontend, an
   * [LxNonLuxWindowRef] is provided as its parent, so that focus returns to the previous
   * dialog when the new one is closed.
   */
  suspend fun <T> performActionWithFocus(frontendFocused: Boolean, action: (suspend () -> T?)? = null): T? {
    val previousValue = isFrontendWindowFocused
    isFrontendWindowFocused = frontendFocused
    try {
      return action?.invoke()
    }
    finally {
      isFrontendWindowFocused = previousValue
    }
  }

  fun isFrontendFocused(): Boolean = isFrontendWindowFocused

  companion object {
    fun getInstance(): WindowFocusFrontendService = service()
  }
}