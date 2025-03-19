// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * Enables/disables Code Vision for the current project.
 * This action does not affect the `Inlay Hints` configurable because inlay settings are application level.
 */
internal class CodeVisionToggleAction : ToggleAction() {

  override fun update(e: AnActionEvent) {
    if (e.project == null) {
      e.presentation.isEnabledAndVisible = false
    } else {
      super.update(e)
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return CodeVisionProjectSettings.getInstance(e.project!!).isEnabledForProject()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    CodeVisionProjectSettings.getInstance(e.project!!).setEnabledForProject(state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
