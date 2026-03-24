// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.progress.ProgressUIUtil
import java.util.function.Consumer

internal class DefaultMethodBreakpointProgressSupport : MethodBreakpointProgressSupport {
  override fun runWithModalProgress(
    project: Project,
    @NlsContexts.Button cancelText: String,
    delayInMillis: Int,
    action: Consumer<in ProgressIndicator>,
  ) {
    val indicator = ProgressWindow(true, false, project, cancelText)
    indicator.setDelayInMillis(delayInMillis)
    ProgressManager.getInstance().executeProcessUnderProgress({ action.accept(indicator) }, indicator)
  }
}
