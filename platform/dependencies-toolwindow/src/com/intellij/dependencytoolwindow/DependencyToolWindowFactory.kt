// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.dependencytoolwindow

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.util.PlatformUtils.*
import icons.PlatformDependencyToolwindowIcons
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds


class DependencyToolWindowFactory : StartupActivity {

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

  override fun runActivity(project: Project) {
    project.lifecycleScope.launch {
      DependenciesToolWindowTabProvider.availableTabsFlow(project)
        .filter { it.isNotEmpty() }
        .first()
      withContext(Dispatchers.toolWindowManager(project)) {
        val messagePointer = DependencyToolWindowBundle.messagePointer("toolwindow.stripe.Dependencies")
        val toolWindowTask = RegisterToolWindowTask.closable(
          id = messagePointer.get(),
          stripeTitle = messagePointer,
          icon = PlatformDependencyToolwindowIcons.ArtifactSmall
        )
        val toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(toolWindowTask)

        toolWindow.contentManager.removeAllContents(true)
        toolWindow.isAvailable = false

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

        val tabsFlow = DependenciesToolWindowTabProvider.availableTabsFlow(project)
          .shareIn(project.lifecycleScope, SharingStarted.Eagerly, 1)

        tabsFlow.onEach { toolWindow.isAvailable = it.isNotEmpty() }
          .flowOn(Dispatchers.EDT)
          .launchIn(project.lifecycleScope)

        toolWindow.isVisibleFlow
          .filter { it }
          .take(1)
          .flatMapLatest { tabsFlow }
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
  }

}

internal val ToolWindow.isVisibleFlow
  get() = flow {
    while (currentCoroutineContext().isActive) {
      emit(isVisible)
      delay(50.milliseconds)
    }
  }