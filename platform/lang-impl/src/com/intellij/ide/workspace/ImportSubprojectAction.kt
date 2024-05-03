// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.TrustedPaths
import com.intellij.idea.ActionsBundle
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.nio.file.Path

class ImportSubprojectActionGroup : ActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (!Registry.`is`("ide.enable.project.workspaces")) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val isWorkspace = WorkspaceSettings.getInstance(project).isWorkspace
    e.presentation.text = when {
      isWorkspace -> ActionsBundle.message("group.ImportSubprojectGroup.text")
      else -> ActionsBundle.message("group.CreateWorkspaceGroup.text")
    }
    e.presentation.isEnabledAndVisible = true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return AnAction.EMPTY_ARRAY
    if (!Registry.`is`("ide.enable.project.workspaces")) return AnAction.EMPTY_ARRAY

    val isWorkspace = WorkspaceSettings.getInstance(project).isWorkspace

    val actions = mutableListOf<AnAction>()
    if (!isWorkspace) actions += CreateWorkspaceFromCurrentAction()
    actions += ImportRecentProjectsActionGroup(isWorkspace)

    actions += listOf(OpenProjectSource, NewProjectSource)
      .mapNotNull { createImportAction(project, isWorkspace, it) }

    return actions.toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private class ImportRecentProjectsActionGroup(val isWorkspace: Boolean)
  : ActionGroup(ActionsBundle.messagePointer("action.ImportRecentProjectsActionGroup.text"), true), DumbAware {

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return AnAction.EMPTY_ARRAY
    val recentProjectsManager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase ?: return AnAction.EMPTY_ARRAY
    return recentProjectsManager.getRecentPaths()
      .filter { it != project.basePath }
      .map { path ->
        val displayName = recentProjectsManager.getDisplayName(path) //NON-NLS
                          ?: recentProjectsManager.getProjectName(path)
        ExistingProjectSource(path, displayName)
      }
      .mapNotNull { source -> createImportAction(project, isWorkspace, source) }
      .toTypedArray()
  }
}

private fun createImportAction(project: Project, isWorkspace: Boolean, source: SubprojectSource): AnAction? {
  if (!source.isAvailable(project, isWorkspace)) return null
  if (isWorkspace) {
    return ImportSubprojectAction(source)
  }
  else {
    return CreateWorkspaceWithAction(source)
  }
}

private class CreateWorkspaceFromCurrentAction : DumbAwareAction(ActionsBundle.message("action.ImportSubproject.FromThisProject.text")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    createWorkspaceFromCurrent(project)
  }
}

private class CreateWorkspaceWithAction(val source: SubprojectSource) : DumbAwareAction() {
  init {
    templatePresentation.setTextWithMnemonic { source.actionText }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    createWorkspaceFromCurrent(project, source)
  }
}

private class ImportSubprojectAction(val source: SubprojectSource) : DumbAwareAction() {
  init {
    templatePresentation.setTextWithMnemonic { source.actionText }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    source.import(project)
  }
}

private fun createWorkspaceFromCurrent(project: Project, vararg additionalSources: SubprojectSource): Boolean {
  val dialog = NewWorkspaceDialog(project)
  if (!dialog.showAndGet()) return false

  val settings = importSettingsFromProject(project, true)
  project.service<MyCoroutineScopeService>().scope.launch  {
    createAndOpenWorkspaceProject(project, dialog.projectPath, dialog.projectName) { workspace ->
      for (importedSetting in settings) {
        importedSetting.applyTo(workspace)
      }
      for (source in additionalSources) {
        source.import(workspace)
      }
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
      WorkspaceSettings.getInstance(workspace).isWorkspace = true
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


private interface SubprojectSource {
  val actionText: TextWithMnemonic

  fun isAvailable(project: Project, isWorkspace: Boolean): Boolean = true
  fun import(workspace: Project): Boolean
}

@Service(Service.Level.PROJECT)
internal class MyCoroutineScopeService(val scope: CoroutineScope)

private class ExistingProjectSource(val projectPath: String, val projectName: @Nls String) : SubprojectSource {
  override val actionText: TextWithMnemonic get() = TextWithMnemonic.fromPlainText(projectName)

  override fun import(workspace: Project): Boolean {
    val projectManagerImpl = ProjectManager.getInstance() as ProjectManagerImpl
    workspace.service<MyCoroutineScopeService>().scope.launch {
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
    return true
  }
}

private object OpenProjectSource : SubprojectSource {
  override val actionText: TextWithMnemonic
    get() = TextWithMnemonic.parse(ActionsBundle.message("action.ImportSubproject.FromProject.text"))

  override fun import(workspace: Project): Boolean {
    val handlers = SubprojectHandler.EP_NAME.extensionList

    val descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
    descriptor.title = LangBundle.message("chooser.title.select.file.or.directory.to.import")
    descriptor.withFileFilter { file -> handlers.any { it.canImportFromFile(workspace, file) } }
    val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, workspace, null)
    val file = chooser.choose(workspace).singleOrNull() ?: return false

    val handler = handlers.find { it.canImportFromFile(workspace, file) } ?: return false
    handler.importFromFile(workspace, file)
    return true
  }
}

private object NewProjectSource : SubprojectSource {
  override val actionText: TextWithMnemonic get() = TextWithMnemonic.parse(ActionsBundle.message("action.ImportSubproject.NewProject.text"))

  override fun isAvailable(project: Project, isWorkspace: Boolean): Boolean = false // not implemented

  override fun import(workspace: Project): Boolean {
    TODO()
  }
}

class WorkspaceAttachProcessor : ProjectAttachProcessor() {

  override fun attachToProject(project: Project, projectDir: Path, callback: ProjectOpenedCallback?): Boolean {
    val isWorkspace = WorkspaceSettings.getInstance(project).isWorkspace
    val source = ExistingProjectSource(projectDir.toString(), "") // display name is unused

    if (!source.isAvailable(project, isWorkspace)) return false
    if (isWorkspace) {
      return source.import(project)
    }
    else {
      return createWorkspaceFromCurrent(project, source)
    }
  }

  override val isEnabled: Boolean = Registry.`is`("ide.enable.project.workspaces")

  override fun getActionText(project: Project): String {
    if (WorkspaceSettings.getInstance(project).isWorkspace) {
      return LangBundle.message("prompt.open.project.attach.to.workspace.button.attach")
    }
    else {
      return LangBundle.message("prompt.open.project.create.workspace.button.attach")
    }
  }

  override fun getDescription(project: Project): String {
    if (WorkspaceSettings.getInstance(project).isWorkspace) {
      return LangBundle.message("prompt.open.project.attach.to.workspace")
    }
    else {
      return LangBundle.message("prompt.open.project.create.workspace")
    }
  }
}