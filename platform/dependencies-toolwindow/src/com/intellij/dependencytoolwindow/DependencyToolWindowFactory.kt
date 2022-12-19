// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.dependencytoolwindow

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*


class DependencyToolWindowFactory : ToolWindowFactory {

  companion object {

    const val DEPENDENCIES_TOOL_WINDOW_ID = "Dependencies"

    fun activateToolWindow(project: Project, id: DependenciesToolWindowTabProvider.Id, action: () -> Unit) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(DEPENDENCIES_TOOL_WINDOW_ID) ?: return
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

    toolWindow.contentManager.addSelectionChangedListener { event ->
      val actionToolWindow = event.content.component as? HasToolWindowActions
      if (toolWindow is ToolWindowEx) {
        toolWindow.setAdditionalGearActions(null)
        actionToolWindow?.also { toolWindow.setAdditionalGearActions(it.gearActions) }
      }
      toolWindow.setTitleActions(emptyList())
      actionToolWindow?.titleActions
        ?.also { toolWindow.setTitleActions(it.toList()) }
    }

    toolWindow.contentManager.removeAllContents(true)

    DependenciesToolWindowTabProvider.availableTabsFlow(project)
      .flowOn(project.lifecycleScope.dispatcher)
      .map { it.map { provider -> provider.provideTab(project) } }
      .onEach { change ->
        val removedContent = toolWindow.contentManager.contents.filter { it !in change }.toSet()
        val newContent = change.filter { it !in toolWindow.contentManager.contents }
        val contentOrder = toolWindow.contentManager
          .contents
          .toList()
          .minus(removedContent)
          .plus(newContent)
          .sortedBy { it.toolwindowTitle }
          .mapIndexed { index, content -> content to index }
          .toMap()
        removedContent.forEach { toolWindow.contentManager.removeContent(it, true) }
        newContent.forEach { content ->
          contentOrder[content]?.let { order -> toolWindow.contentManager.addContent(content, order) }
          ?: toolWindow.contentManager.addContent(content)
        }
      }
      .flowOn(Dispatchers.EDT)
      .launchIn(project.lifecycleScope)

    project.lookAndFeelFlow
      .onEach { toolWindow.contentManager.component.invalidate() }
      .onEach { toolWindow.contentManager.component.repaint() }
      .flowOn(Dispatchers.EDT)
      .launchIn(project.lifecycleScope)
  }

}
