// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.combined.CombinedDiffRegistry
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.CombinedDiffToggle
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.project.DumbAware

internal class CombinedDiffToggleAction : ToggleAction(), DumbAware {

  override fun update(e: AnActionEvent) {
    super.update(e)
    val diffModeToggle = getDiffModeToggle(e)
    e.presentation.isEnabledAndVisible = diffModeToggle != null
    e.presentation.isMultiChoice = false

    val needNewBadge = diffModeToggle != null && !diffModeToggle.isCombinedDiffEnabled && CombinedDiffRegistry.showBadge()
    e.presentation.putClientProperty(ActionMenu.SECONDARY_ICON, if (needNewBadge) AllIcons.General.New_badge else null)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val diffModeToggle = getDiffModeToggle(e) ?: return false
    return diffModeToggle.isCombinedDiffEnabled
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val diffModeToggle = getDiffModeToggle(e) ?: return
    diffModeToggle.isCombinedDiffEnabled = state
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun getDiffModeToggle(e: AnActionEvent): CombinedDiffToggle? {
    val context = e.getData(DiffDataKeys.DIFF_CONTEXT) ?: return null
    return context.getUserData(DiffUserDataKeysEx.COMBINED_DIFF_TOGGLE)
  }
}