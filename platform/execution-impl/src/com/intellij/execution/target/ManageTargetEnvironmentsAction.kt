// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.project.DumbAwareAction

class ManageTargetEnvironmentsAction : DumbAwareAction(ExecutionBundle.message("action.manage.targets.text")) {
  override fun actionPerformed(e: AnActionEvent) {
    TargetEnvironmentsConfigurable(e.project!!).openForEditing()
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = RunTargetsEnabled.get() &&
                                         TargetEnvironmentType.EXTENSION_NAME.extensionList.isNotEmpty()
  }
}