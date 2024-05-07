// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.TrustedPaths
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

internal abstract class BaseWorkspaceAction: DumbAwareAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = Registry.`is`("ide.enable.project.workspaces", false)
  }
}

internal open class CreateWorkspaceAction: BaseWorkspaceAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = requireNotNull(e.project)
    createWorkspace(project)
  }
}

internal fun createWorkspace(project: Project): Boolean {
  val dialog = NewWorkspaceDialog(project, listOf(requireNotNull (project.basePath)))
  if (!dialog.showAndGet()) return false
  val settings = importSettingsFromProject(project, true)
  project.service<MyCoroutineScopeService>().scope.launch {
    createAndOpenWorkspaceProject(project, dialog.projectPath, dialog.projectName) { workspace ->
      for (importedSetting in settings) {
        importedSetting.applyTo(workspace)
      }
      dialog.selectedPaths.forEach({ linkToWorkspace(workspace, it) })
    }
  }
  return true
}

private fun importSettingsFromProject(project: Project, newWorkspace: Boolean): List<ImportedProjectSettings> {
  val settings = mutableListOf<ImportedProjectSettings>()
  val handlers = SubprojectHandler.EP_NAME.extensionList
  for (handler in handlers) {
    settings.addIfNotNull(handler.importFromProject(project, newWorkspace))
  }

  val importers = WorkspaceSettingsImporter.EP_NAME.extensionList
  for (importer in importers) {
    settings.addIfNotNull(importer.importFromProject(project, newWorkspace))
  }
  return settings
}

@Service(Service.Level.PROJECT)
internal class MyCoroutineScopeService(val scope: CoroutineScope)

private fun getCoroutineScope(workspace: Project) = workspace.service<MyCoroutineScopeService>().scope

internal fun linkToWorkspace(workspace: Project, projectPath: String) {
  val projectManagerImpl = ProjectManager.getInstance() as ProjectManagerImpl
  getCoroutineScope(workspace).launch {
    val referentProject = projectManagerImpl.loadProject(Path.of(projectPath), false, false)
    try {
      val settings = importSettingsFromProject(referentProject, false)
      for (importedSettings in settings) {
        importedSettings.applyTo(workspace)
      }
    }
    finally {
      // TODO: fix 'already disposed' failures
      withContext(Dispatchers.EDT) {
        projectManagerImpl.forceCloseProject(referentProject)
      }
    }
  }
}

private fun createAndOpenWorkspaceProject(project: Project,
                                          workspacePath: Path,
                                          projectName: String?,
                                          initTask: suspend CoroutineScope.(workspace: Project) -> Unit) {
  val options = OpenProjectTask {
    projectToClose = project
    this.projectName = projectName
    forceReuseFrame = true
    isNewProject = true
    isProjectCreatedWithWizard = true
    isRefreshVfsNeeded = true
    beforeOpen = { workspace ->
      setWorkspace(workspace)
      withContext(Dispatchers.EDT) {
        initTask(workspace)
      }
      true
    }
  }
  TrustedPaths.getInstance().setProjectPathTrusted(workspacePath, true)
  val workspace = ProjectManagerEx.getInstanceEx().openProject(workspacePath, options) ?: return
  activateProjectToolwindowLater(workspace)
}

private fun activateProjectToolwindowLater(workspace: Project) {
  StartupManager.getInstance(workspace).runAfterOpened {
    invokeLater {
      if (workspace.isDisposed) return@invokeLater
      val toolWindow = ToolWindowManager.getInstance(workspace).getToolWindow(ToolWindowId.PROJECT_VIEW)
      toolWindow?.activate(null)
    }
  }
}