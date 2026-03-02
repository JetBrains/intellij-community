// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.progress.ProgressUIUtil
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

@ApiStatus.Internal
interface MethodBreakpointProgressSupport {
  /**
   * Runs the given [action] on the **current thread** with a visible modal progress indicator.
   *
   * This is needed for Java debugger code that must execute synchronously (on the same thread)
   * while showing modal progress to the user (e.g., during method breakpoint emulation).
   */
  fun runWithModalProgress(
    project: Project,
    @NlsContexts.Button cancelText: String,
    delayInMillis: Int = ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS.toInt(),
    action: Consumer<in ProgressIndicator>,
  )

  companion object {
    @JvmStatic
    fun getInstance(): MethodBreakpointProgressSupport {
      return service<MethodBreakpointProgressSupport>()
    }
  }
}
