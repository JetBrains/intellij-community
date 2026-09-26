// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.impl.showTemplatesDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EditRunConfigurationTemplatesForNewProjectsAction : DumbAwareAction(ExecutionBundle.message("edit.configuration.templates.action")) {
  override fun actionPerformed(e: AnActionEvent) {
    showTemplatesDialog(ProjectManager.getInstance().defaultProject, null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}