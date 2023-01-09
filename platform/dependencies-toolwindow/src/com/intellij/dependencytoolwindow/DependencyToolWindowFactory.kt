// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.dependencytoolwindow

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.PlatformUtils.*
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*


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
    val scope = CoroutineScope(
      AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher() + CoroutineName(this::class.qualifiedName!!)
    )

    project.onDispose { scope.cancel("Disposing project") }

    scope.launch {
      DependenciesToolWindowTabProvider.awaitFirstAvailable(project)
      ToolWindowManager.awaitToolWindows(project)
      withContext(Dispatchers.EDT) {
        project.createDependenciesToolwindow().initialize(scope)
      }
    }
  }
}
