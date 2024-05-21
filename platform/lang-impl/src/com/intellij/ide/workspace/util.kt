// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal val isWorkspaceSupportEnabled get() = Registry.`is`("ide.enable.project.workspaces", true)

@Service(Service.Level.PROJECT)
internal class MyCoroutineScopeService(val scope: CoroutineScope)

internal fun getCoroutineScope(workspace: Project) = workspace.service<MyCoroutineScopeService>().scope

internal fun getHandlers(file: VirtualFile): List<SubprojectHandler> =
  SubprojectHandler.EP_NAME.extensionList.filter { it.canImportFromFile(file) }

internal fun addToWorkspace(project: Project, projectPaths: List<String>) {
  getCoroutineScope(project).launch {
    projectPaths.forEach { s ->
      linkToWorkspace(project, s)
    }
  }
}

internal fun removeSubprojects(subprojects: Collection<Subproject>) {
  subprojects.groupBy { it.handler }.forEach {
    it.key.removeSubprojects(it.value)
  }
}