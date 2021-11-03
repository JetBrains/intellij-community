// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.DiffDataKeys.DIFF_VIEWER
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.util.DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

class ToggleDiffAligningModeAction : DumbAwareToggleAction() {

  override fun update(e: AnActionEvent) {
    val viewer = e.getData(DIFF_VIEWER)
    val available = e.project != null
                    && viewer is SimpleDiffViewer
                    && !DiffUtil.isUserDataFlagSet(ALIGNED_TWO_SIDED_DIFF, viewer.request)
    if (!available) {
      e.presentation.isEnabledAndVisible = available
      return
    }

    super.update(e)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val viewer = e.getData(DIFF_VIEWER) as? SimpleDiffViewer ?: return false
    return TextDiffViewerUtil.getTextSettings(viewer.context).isEnableAligningChangesMode
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val viewer = e.getData(DIFF_VIEWER) as SimpleDiffViewer

    TextDiffViewerUtil.getTextSettings(viewer.context).isEnableAligningChangesMode = state
    viewer.rediff()
  }
}
