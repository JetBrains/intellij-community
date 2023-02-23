// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import javax.swing.Icon

object ReviewDetailsUIUtil {
  fun getRequestStateText(state: RequestState): @NlsSafe String = when (state) {
    RequestState.OPENED -> CollaborationToolsBundle.message("review.details.review.state.open")
    RequestState.CLOSED -> CollaborationToolsBundle.message("review.details.review.state.closed")
    RequestState.MERGED -> CollaborationToolsBundle.message("review.details.review.state.merged")
    RequestState.DRAFT -> CollaborationToolsBundle.message("review.details.review.state.draft")
  }

  fun getReviewStateIcon(reviewState: ReviewState): Icon = when (reviewState) {
    ReviewState.ACCEPTED -> AllIcons.RunConfigurations.TestPassed
    ReviewState.WAIT_FOR_UPDATES -> AllIcons.RunConfigurations.TestError
    ReviewState.NEED_REVIEW -> AllIcons.RunConfigurations.TestFailed
  }

  fun getReviewStateText(reviewState: ReviewState, reviewer: String): @Nls String = when (reviewState) {
    ReviewState.ACCEPTED -> CollaborationToolsBundle.message("review.details.status.reviewer.approved", reviewer)
    ReviewState.WAIT_FOR_UPDATES -> CollaborationToolsBundle.message("review.details.status.reviewer.wait.for.updates", reviewer)
    ReviewState.NEED_REVIEW -> CollaborationToolsBundle.message("review.details.status.reviewer.need.review", reviewer)
  }

  val indentTop: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 12, newUI = 16)
  val indentBottom: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 15, newUI = 18)
  val indentLeft: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 13, newUI = 17)
  val indentRight: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 13, newUI = 13)

  val gapBetweenTitleAndDescription: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 8, newUI = 8)
  val gapBetweenDescriptionAndCommits: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 18, newUI = 22)
  val gapBetweenCommitsAndCommitInfo: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 9, newUI = 15)
  val gapBetweenCommitsBrowserAndStatusChecks: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 6, newUI = 6)
  val gapBetweenCheckAndActions: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 10, newUI = 10)
  val gapBetweenCommitInfoAndCommitsBrowser: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 12, newUI = 12)

  val statusChecksMaxHeight: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 143, newUI = 143)
  val commitInfoMaxHeight: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 100, newUI = 100)
}