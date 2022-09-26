// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.dependencytoolwindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import icons.PlatformDependencyToolwindowIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

@Service(Level.PROJECT)
internal class ContentIdMapService(project: Project) {
  val idMap: StateFlow<Map<DependenciesToolWindowTabProvider.Id, DependenciesToolWindowTabProvider>> =
    DependenciesToolWindowTabProvider.availableTabsFlow(project)
    .map { it.associateBy { it.id } }
    .stateIn(project.lifecycleScope, SharingStarted.Eagerly, emptyMap())
}

class DependencyToolWindowFactory : ProjectPostStartupActivity {
  companion object {
    const val toolWindowId = "Dependencies"

    private fun getToolWindow(project: Project) = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)

    fun activateToolWindow(project: Project, id: DependenciesToolWindowTabProvider.Id, action: () -> Unit) {
      val toolWindow = getToolWindow(project) ?: return
      toolWindow.activate(action, true, true)
      project.contentIdMap[id]
        ?.provideTab(project)
        ?.let { toolWindow.contentManager.setSelectedContent(it) }
    }
  }

  override suspend fun execute(project: Project) {
    withContext(Dispatchers.toolWindowManager(project)) {
      DependenciesToolWindowTabProvider.availableTabsFlow(project)
        .filter { it.isNotEmpty() }
        .take(1)
        .map {
          RegisterToolWindowTask.closable(
            id = toolWindowId,
            stripeTitle = DependencyToolWindowBundle.messagePointer("toolwindow.stripe.Dependencies"),
            icon = PlatformDependencyToolwindowIcons.ArtifactSmall
          )
        }
        .map { toolWindowTask -> ToolWindowManager.getInstance(project).registerToolWindow(toolWindowTask) }
        .onEach { project.contentIdMap /* init service only */ }
        .collect { toolWindow -> initializeToolWindow(toolWindow, project) }
    }
  }
}

