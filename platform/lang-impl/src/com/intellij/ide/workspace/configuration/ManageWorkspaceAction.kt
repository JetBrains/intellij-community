// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace.configuration

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.workspace.addToWorkspace
import com.intellij.ide.workspace.getAllSubprojects
import com.intellij.ide.workspace.projectView.isWorkspaceNode
import com.intellij.ide.workspace.removeSubprojects
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ex.ProjectEx

internal open class ManageWorkspaceAction: BaseWorkspaceAction(true) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = requireNotNull(e.project)
    val dialog = ManageWorkspaceDialog(project)
    if (!dialog.showAndGet()) return

    if (dialog.projectName != project.name) {
      (project as ProjectEx).setProjectName(dialog.projectName)
      ProjectView.getInstance(project).currentProjectViewPane?.updateFromRoot(true)
    }
    val subprojects = getAllSubprojects(project)
    val set = dialog.projectPaths.toSet()
    val removed = subprojects.filter { !set.contains(it.projectPath) }
    removeSubprojects(project, removed)

    val added = dialog.projectPaths.filter { !subprojects.any { subproject -> subproject.projectPath == it } }
    addToWorkspace(project, added)
  }

  override fun update(e: AnActionEvent) {
    if (ActionPlaces.PROJECT_VIEW_POPUP == e.place) {
      e.presentation.isEnabledAndVisible = false
    }
    else {
      super.update(e)
    }
  }
}

internal class ManageWorkspacePopupAction: ManageWorkspaceAction() {
  init {
    templatePresentation.text = ActionsBundle.message("action.ManageWorkspace.text")
  }
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isWorkspaceNode(e)
  }
}