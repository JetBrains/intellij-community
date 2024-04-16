// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.DeleteProvider
import com.intellij.ide.TitledHandler
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry

class RemoveSubprojectActionGroup : ActionGroup(), DumbAware {
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

    e.presentation.isEnabledAndVisible = WorkspaceSettings.getInstance(project).isWorkspace
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return AnAction.EMPTY_ARRAY
    if (!Registry.`is`("ide.enable.project.workspaces")) return AnAction.EMPTY_ARRAY
    if (!WorkspaceSettings.getInstance(project).isWorkspace) return AnAction.EMPTY_ARRAY

    val handlers = SubprojectHandler.EP_NAME.extensionList
    val subprojects = handlers
      .flatMap { it.getSubprojects(project) }
      .sortedWith(compareBy<Subproject> { it.name }.thenBy { it.projectPath })

    return subprojects.map { RemoveSubprojectAction(it) }.toTypedArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private class RemoveSubprojectAction(val subproject: Subproject) : DumbAwareAction() {
  init {
    templatePresentation.setText(subproject.name, false)
  }

  override fun actionPerformed(e: AnActionEvent) {
    subproject.removeSubproject()
  }
}

class SubprojectDeleteProvider(val selected: Collection<Subproject>) : DeleteProvider, TitledHandler {
  override fun deleteElement(dataContext: DataContext) {
    for (subproject in selected) {
      subproject.removeSubproject()
    }
  }

  override fun canDeleteElement(dataContext: DataContext) = selected.isNotEmpty()

  override fun getActionTitle(): String {
    val subproject = selected.singleOrNull()
    if (subproject != null) {
      return ActionsBundle.message("action.remove.workspace.subproject.x.text", subproject.name)
    }
    else {
      return ActionsBundle.message("action.remove.workspace.subprojects.text")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
