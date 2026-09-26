// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.project.DumbAwareAction

internal class NavigateToChangeMarkerAction(private val viewer: MergeThreesideViewer, private val goToNext: Boolean) : DumbAwareAction() {

  init {
    // TODO: reuse ShowChangeMarkerAction
    copyFrom(this, if (goToNext) "VcsShowNextChangeMarker" else "VcsShowPrevChangeMarker")
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.setEnabled(viewer.textSettings.isEnableLstGutterMarkersInMerge)
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!viewer.myLineStatusTracker.isValid()) return

    val line = viewer.editor.getCaretModel().logicalPosition.line
    val targetRange = if (goToNext) viewer.myLineStatusTracker.getNextRange(line) else viewer.myLineStatusTracker.getPrevRange(line)
    if (targetRange != null) {
      MergeThreesideLineStatusMarkerRenderer(viewer.myLineStatusTracker, viewer).scrollAndShow(viewer.editor, targetRange)
    }
  }
}
