// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ex.ProjectEx
import kotlinx.coroutines.launch

internal class ManageWorkspaceAction: BaseWorkspaceAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = requireNotNull(e.project)
    val subprojects = SubprojectHandler.getAllSubprojects(project)
    val subprojectPaths = subprojects.map { it.projectPath }
    val dialog = NewWorkspaceDialog(project, subprojectPaths)
    if (!dialog.showAndGet()) return

    if (dialog.projectName != project.name) {
      (project as ProjectEx).setProjectName(dialog.projectName)
      ProjectView.getInstance(project).currentProjectViewPane?.updateFromRoot(true)
    }
    val set = dialog.selectedPaths.toSet()
    val removed = subprojects.filter { !set.contains(it.projectPath) }
    removed.forEach {
      it.removeSubproject()
    }

    val added = dialog.selectedPaths.filter { !subprojectPaths.contains(it) }
    getCoroutineScope(project).launch {
      added.forEach { s ->
        linkToWorkspace(project, s)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && e.project?.isWorkspace == true
  }
}