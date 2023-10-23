// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.meetNewUi

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ExperimentalUI

private class MeetNewUiToolWindowFactory : ToolWindowFactory, DumbAware {
  override suspend fun isApplicableAsync(project: Project): Boolean {
    return ExperimentalUI.isNewUI() &&
           Registry.`is`("ide.experimental.ui.meetNewUi", true) &&
           (serviceAsync<PropertiesComponent>().getBoolean(ExperimentalUI.NEW_UI_FIRST_SWITCH) ||
            MeetNewUiCustomization.firstOrNull()?.shouldCreateToolWindow() == true) &&
           !isNotificationSilentMode(project)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val title = IdeBundle.message("meetnewui.toolwindow.title")
    toolWindow.title = title
    toolWindow.stripeTitle = title
    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(MeetNewUiToolWindow(project, toolWindow), null, true)
    contentManager.addContent(content)
  }

  override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
    if (MeetNewUiCustomization.firstOrNull()?.showToolWindowOnStartup() != false) {
      toolWindowManager.invokeLater {
        toolWindow.activate(null)
      }
    }
    serviceAsync<PropertiesComponent>().unsetValue(ExperimentalUI.NEW_UI_FIRST_SWITCH)
  }
}
