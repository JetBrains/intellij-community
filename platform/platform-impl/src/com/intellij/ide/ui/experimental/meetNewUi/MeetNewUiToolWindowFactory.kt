// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.meetNewUi

import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.ApiStatus

@Deprecated("Will be removed at all")
@ApiStatus.ScheduledForRemoval
internal class MeetNewUiToolWindowFactory : ToolWindowFactory, DumbAware {
  override suspend fun isApplicableAsync(project: Project): Boolean {
    return false
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
  }
}
