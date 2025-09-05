// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.idea.AppModeAssertions
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ex.NoProjectStateHandler
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.util.concurrency.annotations.RequiresEdt

internal class WelcomeScreenNoProjectStateHandler : NoProjectStateHandler {
  override fun canHandle(): Boolean {
    if (!AppModeAssertions.isMonolith()) return false

    val openProjects = ProjectManager.getInstance().openProjects
    return isNonModalWelcomeScreenEnabled && openProjects.isEmpty()
  }

  @RequiresEdt
  override fun handle() {
    FUSProjectHotStartUpMeasurer.reportWelcomeScreenShown()
    WelcomeScreenProjectProvider.createOrOpenWelcomeScreenProjectAsync()
  }
}
