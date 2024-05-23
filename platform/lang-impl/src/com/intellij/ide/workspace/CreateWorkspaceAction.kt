// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.TrustedPaths
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.startup.StartupManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

internal abstract class BaseWorkspaceAction(private val workspaceOnly: Boolean): DumbAwareAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = isWorkspaceSupportEnabled &&
                                         project != null &&
                                         (workspaceOnly && project.isWorkspace
                                         || !workspaceOnly && SubprojectHandler.getAllSubprojects(project).isNotEmpty())
  }
}

internal open class CreateWorkspaceAction: BaseWorkspaceAction(false) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = requireNotNull(e.project)
    createWorkspace(project)
  }
}

@RequiresEdt
internal fun createWorkspace(project: Project) {
  val subprojects = SubprojectHandler.getAllSubprojects(project).associateBy { it.projectPath }
  val dialog = NewWorkspaceDialog(project, subprojects.values, true)
  if (!dialog.showAndGet()) return

  ApplicationManager.getApplication().executeOnPooledThread {
    val workspace = createAndOpenWorkspaceProject(project, dialog.projectPath, dialog.projectName)
                    ?: return@executeOnPooledThread
    StartupManager.getInstance(workspace).runAfterOpened {
      addToWorkspace(workspace, dialog.projectPaths)
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

internal suspend fun linkToWorkspace(workspace: Project, projectPath: String) {
  val projectManagerImpl = blockingContext { ProjectManager.getInstance() as ProjectManagerImpl }
  val referentProject = blockingContext { projectManagerImpl.loadProject(Path.of(projectPath), false, false) }
  try {
    val settings = importSettingsFromProject(referentProject)
    for (importedSettings in settings) {
      importedSettings.applyTo(workspace)
    }
  }
  finally {
    (referentProject as ComponentManagerEx).getCoroutineScope().coroutineContext.job.cancelAndJoin()
    withContext(Dispatchers.EDT) {
      projectManagerImpl.forceCloseProject(referentProject)
    }
  }
}

private fun createAndOpenWorkspaceProject(project: Project,
                                          workspacePath: Path,
                                          projectName: String): Project? {
  val options = OpenProjectTask {
    projectToClose = project
    this.projectName = projectName
    forceReuseFrame = true
    isNewProject = true
    isProjectCreatedWithWizard = true
    isRefreshVfsNeeded = true
    beforeOpen = { workspace ->
      setWorkspace(workspace)
      true
    }
  }
  Files.createDirectories(workspacePath)
  TrustedPaths.getInstance().setProjectPathTrusted(workspacePath, true)
  return ProjectManagerEx.getInstanceEx().openProject(workspacePath, options)
}