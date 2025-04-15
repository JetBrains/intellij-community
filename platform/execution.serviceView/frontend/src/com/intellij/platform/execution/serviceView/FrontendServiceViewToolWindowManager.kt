// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.execution.services.ServiceViewManager
import com.intellij.execution.services.ServiceViewToolWindowFactory
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow

internal class FrontendServiceViewToolWindowManager(
  private val project: Project,
) : ServiceViewToolWindowFactory.CompatibilityDelegate {

  override fun shouldBeAvailable(): Boolean {
    // TODO move impl to this class
    return Registry.`is`("services.view.split.enabled") && (ServiceViewManager.getInstance(project) as ServiceViewManagerImpl).shouldBeAvailable()
  }

  override fun createToolWindowContent(toolWindow: ToolWindow) {
    if (!Registry.`is`("services.view.split.enabled")) {
      thisLogger().warn("Monolith services implementation is going to be used. Frontend toolwindow is disabled")
      return
    }
    // TODO move impl to this class
    return (ServiceViewManager.getInstance(project) as ServiceViewManagerImpl).createToolWindowContent(toolWindow)
  }
}
