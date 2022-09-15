// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dependencytoolwindow

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

internal fun initializeToolWindow(toolWindow: ToolWindow, project: Project) {
  toolWindow.contentManager.addSelectionChangedListener { event ->
    if (toolWindow is ToolWindowEx) {
      toolWindow.setAdditionalGearActions(null)
      (event.content.component as? HasToolWindowActions)
        ?.also { toolWindow.setAdditionalGearActions(it.gearActions) }
    }
    toolWindow.setTitleActions(emptyList())
    (event.content.component as? HasToolWindowActions)
      ?.titleActions
      ?.also { toolWindow.setTitleActions(it.toList()) }
  }

  toolWindow.isAvailable = false
  toolWindow.contentManager.removeAllContents(true)

  DependenciesToolWindowTabProvider.availableTabsFlow(project)
    .flowOn(project.lifecycleScope.dispatcher)
    .map { it.map { provider -> provider.provideTab(project) } }
    .onEach { change ->
      val removedContent = toolWindow.contentManager.contents.filter { it !in change }.toSet()
      val newContent = change.filter { it !in toolWindow.contentManager.contents }
      val contentOrder = (toolWindow.contentManager.contents.toList() - removedContent + newContent)
        .sortedBy { it.toolwindowTitle }
        .mapIndexed { index, content -> content to index }
        .toMap()
      removedContent.forEach { toolWindow.contentManager.removeContent(it, true) }
      newContent.forEach { content ->
        contentOrder[content]?.let { order -> toolWindow.contentManager.addContent(content, order) }
        ?: toolWindow.contentManager.addContent(content)
      }
      toolWindow.isAvailable = change.isNotEmpty()
    }
    .flowOn(Dispatchers.EDT)
    .launchIn(project.lifecycleScope)

  project.lookAndFeelFlow
    .onEach {
      toolWindow.contentManager.component.invalidate()
      toolWindow.contentManager.component.repaint()
    }
    .flowOn(Dispatchers.EDT)
    .launchIn(project.lifecycleScope)
}

@Suppress("FunctionName")
internal fun SelectionChangedListener(action: (ContentManagerEvent) -> Unit): ContentManagerListener {
  return object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      action(event)
    }
  }
}

internal fun ContentManager.addSelectionChangedListener(action: (ContentManagerEvent) -> Unit): ContentManagerListener {
  return SelectionChangedListener(action).also(::addContentManagerListener)
}

internal const val BUNDLE_NAME = "messages.dependenciesToolwindow"

