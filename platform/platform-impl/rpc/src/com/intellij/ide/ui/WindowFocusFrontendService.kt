// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Service
class WindowFocusFrontendService {
  private var isFrontendWindowFocused: Boolean = false

  /**
   * Performs the given [action] while marking all dialogs created during its execution
   * with `isFrontendWindowFocused`. When such a dialog is luxed to the frontend, an
   * [LxNonLuxWindowRef] is provided as its parent, which means the latest focused window
   * will be used as a parent for newly appearing Lux-ed windows.
   */
  @RequiresEdt
  fun <T> performActionWithFocus(frontendFocused: Boolean, action: (() -> T?)? = null): T? {
    ThreadingAssertions.assertEventDispatchThread()
    val previousValue = isFrontendWindowFocused
    isFrontendWindowFocused = frontendFocused
    try {
      return action?.invoke()
    }
    finally {
      isFrontendWindowFocused = previousValue
    }
  }

  @RequiresEdt
  fun isFrontendFocused(): Boolean {
    ThreadingAssertions.assertEventDispatchThread()
    return isFrontendWindowFocused
  }

  companion object {
    fun getInstance(): WindowFocusFrontendService = service()
  }
}