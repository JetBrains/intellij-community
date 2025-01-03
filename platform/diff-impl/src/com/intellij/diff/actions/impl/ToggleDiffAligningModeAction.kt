// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.DiffDataKeys.DIFF_CONTEXT
import com.intellij.diff.tools.util.DiffDataKeys.DIFF_VIEWER
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.util.DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

internal class ToggleDiffAligningModeAction : DumbAwareToggleAction() {

  override fun update(e: AnActionEvent) {
    val viewer = e.getData(DIFF_VIEWER)
    val project = e.project

    val available = project != null &&
                    viewer is SimpleDiffViewer &&
                    viewer.isAligningViewModeSupported &&
                    viewer.request.getUserData(ALIGNED_TWO_SIDED_DIFF) == null &&
                    viewer.context.getUserData(ALIGNED_TWO_SIDED_DIFF) == null
    if (!available) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.update(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val diffContext = e.getData(DIFF_CONTEXT) ?: return false
    return TextDiffViewerUtil.getTextSettings(diffContext).isEnableAligningChangesMode
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val diffContext = e.getData(DIFF_CONTEXT) ?: return
    TextDiffViewerUtil.getTextSettings(diffContext).isEnableAligningChangesMode = state
  }
}
