// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.dependencytoolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.flow.*


class DependencyToolWindowFactory : ToolWindowFactory {
  companion object {
    const val toolWindowId = "Dependencies"

    private fun getToolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)

    fun activateToolWindow(project: Project, id: DependenciesToolWindowTabProvider.Id, action: () -> Unit) {
      val toolWindow = getToolWindow(project) ?: return
      toolWindow.activate(action, true, true)
      DependenciesToolWindowTabProvider.extensions(project)
        .filter { it.isAvailable(project) }
        .associateBy { it.id }
        .get(id)
        ?.provideTab(project)
        ?.let { toolWindow.contentManager.setSelectedContent(it) }
    }

  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.initializeToolWindow(project)
  }
}
