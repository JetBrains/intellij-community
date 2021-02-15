// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class ConfigureFilesNestingAction : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = isFileNestingAllowed(event)
  }

  private fun isFileNestingAllowed(event: AnActionEvent): Boolean {
    val project = event.project ?: return false
    val view = ProjectView.getInstance(project)
    return view.currentProjectViewPane?.isFileNestingEnabled ?: false
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val dialog = FileNestingInProjectViewDialog(project)
    dialog.reset(ProjectViewState.getInstance(project).useFileNestingRules)
    if (dialog.showAndGet()) {
      val view = ProjectView.getInstance(project)
      dialog.apply { view.setUseFileNestingRules(it) }
      view.currentProjectViewPane?.updateFromRoot(true)
    }
  }
}
