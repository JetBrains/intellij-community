// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.TrustedPaths
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.nio.file.Files
import java.nio.file.Path

internal abstract class BaseWorkspaceAction(private val workspaceOnly: Boolean): DumbAwareAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = isWorkspaceSupportEnabled &&
                                         project != null &&
                                         (workspaceOnly && project.isWorkspace
                                         || !workspaceOnly && getAllSubprojects(project).isNotEmpty())
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
  val subprojects = getAllSubprojects(project).associateBy { it.projectPath }
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