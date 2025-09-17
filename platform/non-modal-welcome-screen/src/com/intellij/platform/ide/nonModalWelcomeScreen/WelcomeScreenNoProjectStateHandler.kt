// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ex.NoProjectStateHandler
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.util.concurrency.annotations.RequiresEdt

private class WelcomeScreenNoProjectStateHandler : NoProjectStateHandler {
  override fun canHandle(): Boolean {
    return isNonModalWelcomeScreenEnabled && ProjectManager.getInstanceIfCreated()?.openProjects.isNullOrEmpty()
  }

  @RequiresEdt
  override fun handle() {
    FUSProjectHotStartUpMeasurer.reportWelcomeScreenShown()
    WelcomeScreenProjectProvider.createOrOpenWelcomeScreenProjectAsync()
  }
}
