// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.CommonBundle
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.*
import java.nio.file.Path

internal val isWorkspaceSupportEnabled get() = Registry.`is`("ide.enable.project.workspaces", true)

@Service(Service.Level.PROJECT)
internal class MyCoroutineScopeService(val scope: CoroutineScope)

internal fun getCoroutineScope(workspace: Project) = workspace.service<MyCoroutineScopeService>().scope

internal fun getHandlers(file: VirtualFile): List<SubprojectHandler> =
  SubprojectHandler.EP_NAME.extensionList.filter { it.canImportFromFile(file) }

internal fun addToWorkspace(workspace: Project, projectPaths: List<String>) {
  getCoroutineScope(workspace).launch {
    projectPaths.forEach { s ->
      linkToWorkspace(workspace, s)
    }
  }
}

internal fun removeSubprojects(workspace: Project, subprojects: Collection<Subproject>) {
  subprojects.groupBy { it.handler }.forEach {
    it.key.removeSubprojects(workspace, it.value)
  }
}

internal suspend fun linkToWorkspace(workspace: Project, projectPath: String) {
  val projectManagerImpl = blockingContext { ProjectManager.getInstance() as ProjectManagerImpl }
  val referentProject = blockingContext { projectManagerImpl.loadProject(Path.of(projectPath), false, false) }
  var success = false
  try {
    val settings = importSettingsFromProject(referentProject)
    for (importedSettings in settings) {
      if (importedSettings.applyTo(workspace)) {
        success = true
        break
      }
    }
  }
  finally {
    (referentProject as ComponentManagerEx).getCoroutineScope().coroutineContext.job.cancelAndJoin()
    withContext(Dispatchers.EDT) {
      projectManagerImpl.forceCloseProject(referentProject)
      if (!success) {
        Messages.showErrorDialog(workspace, LangBundle.message("dialog.message.project.can.t.be.added.to.workspace", referentProject.name), CommonBundle.getErrorTitle())
      }
    }
  }
}

private fun importSettingsFromProject(project: Project): List<ImportedProjectSettings> {
  val settings = mutableListOf<ImportedProjectSettings>()
  val handlers = SubprojectHandler.EP_NAME.extensionList
  for (handler in handlers) {
    settings.addIfNotNull(handler.importFromProject(project))
  }

  val importers = WorkspaceSettingsImporter.EP_NAME.extensionList
  for (importer in importers) {
    settings.addIfNotNull(importer.importFromProject(project))
  }
  return settings
}