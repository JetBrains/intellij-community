// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Pair

internal class ShowDiffWithBaseAction(
  private val viewer: MergeThreesideViewer,
  private val side: ThreeSide,
) : DumbAwareAction() {

  init {
    val actionId = side.select("Diff.CompareWithBase.Left", "Diff.CompareWithBase.Result", "Diff.CompareWithBase.Right")
    ActionUtil.copyFrom(this, actionId)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.setEnabled(!viewer.isExternalOperationInProgress)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val baseContent = ThreeSide.BASE.select(viewer.mergeRequest.getContents())
    val baseTitle = ThreeSide.BASE.select(viewer.mergeRequest.getContentTitles())

    val otherContent = side.select(viewer.request.getContents())
    val otherTitle = side.select(viewer.request.getContentTitles())

    val request = SimpleDiffRequest(viewer.request.getTitle(), baseContent, otherContent, baseTitle, otherTitle)

    val currentPosition = DiffUtil.getCaretPosition(viewer.currentEditor)

    val resultPosition = viewer.transferPosition(viewer.currentSide, side, currentPosition)
    request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, resultPosition.line))

    DiffManager.getInstance().showDiff(viewer.project, request, DiffDialogHints(null, viewer.component))
  }
}
