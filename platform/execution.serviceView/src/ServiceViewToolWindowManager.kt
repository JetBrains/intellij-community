// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.execution.services.ServiceViewManager
import com.intellij.execution.services.ServiceViewToolWindowFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

internal class ServiceViewToolWindowManager(
  private val project: Project,
) : ServiceViewToolWindowFactory.CompatibilityDelegate {

  override fun shouldBeAvailable(): Boolean {
    // TODO move impl to this class
    return shouldEnableServicesViewInCurrentEnvironment() && (ServiceViewManager.getInstance(project) as ServiceViewManagerImpl).shouldBeAvailable()
  }

  override fun createToolWindowContent(toolWindow: ToolWindow) {
    // TODO move impl to this class
    if (shouldEnableServicesViewInCurrentEnvironment()) {
      return (ServiceViewManager.getInstance(project) as ServiceViewManagerImpl).createToolWindowContent(toolWindow)
    }
  }
}
