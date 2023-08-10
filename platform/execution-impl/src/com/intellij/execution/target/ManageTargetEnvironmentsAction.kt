// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class ManageTargetEnvironmentsAction : DumbAwareAction(ExecutionBundle.message("action.manage.targets.text")) {

  override fun actionPerformed(e: AnActionEvent) {
    TargetEnvironmentsConfigurable(e.project!!).openForEditing()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.project != null
                                         && RunTargetsEnabled.get()
                                         && TargetEnvironmentType.getTargetTypesForRunConfigurations().isNotEmpty()
  }
}