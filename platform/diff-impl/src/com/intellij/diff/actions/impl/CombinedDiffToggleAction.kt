// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.combined.CombinedDiffRegistry
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.CombinedDiffToggle
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionMenu.Companion.shouldConvertIconToDarkVariant
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.LafIconLookup

class CombinedDiffToggleAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    super.update(e)
    val diffModeToggle = getDiffModeToggle(e)
    e.presentation.isEnabledAndVisible = diffModeToggle != null
    e.presentation.putClientProperty(ActionMenu.SECONDARY_ICON, null)

    diffModeToggle ?: return

    if (diffModeToggle.isCombinedDiffEnabled) {
      // It impossible to use ToggleAction here because it doesn't close the popup menu
      // Temporary solution is to add checkmark icon on AnAction
      //
      var checkmark = LafIconLookup.getIcon("checkmark")
      var selectedCheckmark = LafIconLookup.getSelectedIcon("checkmark")
      if (shouldConvertIconToDarkVariant()) {
        checkmark = IconLoader.getDarkIcon(checkmark, true)
        selectedCheckmark = IconLoader.getDarkIcon(selectedCheckmark, true)
      }
      e.presentation.icon = checkmark
      e.presentation.selectedIcon = selectedCheckmark
    }
    else {
      e.presentation.icon = null
      e.presentation.selectedIcon = null
      if (CombinedDiffRegistry.showBadge()) {
        e.presentation.putClientProperty(ActionMenu.SECONDARY_ICON, AllIcons.General.New_badge)
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val diffModeToggle = getDiffModeToggle(e) ?: return
    diffModeToggle.isCombinedDiffEnabled = !diffModeToggle.isCombinedDiffEnabled
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun getDiffModeToggle(e: AnActionEvent): CombinedDiffToggle? {
    val context = e.getData(DiffDataKeys.DIFF_CONTEXT) ?: return null
    return context.getUserData(DiffUserDataKeysEx.COMBINED_DIFF_TOGGLE)
  }
}