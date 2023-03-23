// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.meetNewUi

import com.intellij.icons.ExpUiIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ExperimentalUI

private class MeetNewUiToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun isApplicable(project: Project): Boolean {
    return ExperimentalUI.isNewUI() && Registry.`is`("ide.experimental.ui.meetNewUi")
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val title = IdeBundle.message("meetnewui.toolwindow.title")
    toolWindow.title = title
    toolWindow.stripeTitle = title
    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(MeetNewUiToolWindow(project, toolWindow), null, true)
    contentManager.addContent(content)
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.setIcon(ExpUiIcons.Toolwindow.MeetNewUi)

    val project = toolWindow.project
    val propertiesComponent = PropertiesComponent.getInstance()
    if (isNotificationSilentMode(project) || !propertiesComponent.getBoolean(ExperimentalUI.NEW_UI_FIRST_SWITCH)) {
      return
    }

    propertiesComponent.unsetValue(ExperimentalUI.NEW_UI_FIRST_SWITCH)
    val manager = ToolWindowManager.getInstance(project)
    manager.invokeLater {
      toolWindow.activate(null)
    }
  }
}
