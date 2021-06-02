// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.impl.showTemplatesDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager

class EditRunConfigurationTemplatesForNewProjectsAction : DumbAwareAction(ExecutionBundle.message("edit.configuration.templates.action")) {
  override fun actionPerformed(e: AnActionEvent) {
    showTemplatesDialog(ProjectManager.getInstance().defaultProject, null)
  }
}