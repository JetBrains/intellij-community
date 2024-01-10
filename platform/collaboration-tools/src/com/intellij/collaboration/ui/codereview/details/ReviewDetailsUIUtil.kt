// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.icons.AllIcons
import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Insets
import javax.swing.Icon

object ReviewDetailsUIUtil {
  fun getRequestStateText(state: ReviewRequestState): @NlsSafe String = when (state) {
    ReviewRequestState.OPENED -> CollaborationToolsBundle.message("review.details.review.state.open")
    ReviewRequestState.CLOSED -> CollaborationToolsBundle.message("review.details.review.state.closed")
    ReviewRequestState.MERGED -> CollaborationToolsBundle.message("review.details.review.state.merged")
    ReviewRequestState.DRAFT -> CollaborationToolsBundle.message("review.details.review.state.draft")
  }

  fun getReviewStateIcon(reviewState: ReviewState): Icon = when (reviewState) {
    ReviewState.ACCEPTED -> if (ExperimentalUI.isNewUI()) ExpUiIcons.Status.Success else AllIcons.RunConfigurations.TestPassed
    ReviewState.WAIT_FOR_UPDATES -> if (ExperimentalUI.isNewUI()) ExpUiIcons.Status.Error else AllIcons.RunConfigurations.TestError
    ReviewState.NEED_REVIEW -> if (ExperimentalUI.isNewUI()) ExpUiIcons.Status.Warning else AllIcons.RunConfigurations.TestFailed
  }

  fun getReviewStateText(reviewState: ReviewState, reviewer: String): @Nls String = when (reviewState) {
    ReviewState.ACCEPTED -> CollaborationToolsBundle.message("review.details.status.reviewer.approved", reviewer)
    ReviewState.WAIT_FOR_UPDATES -> CollaborationToolsBundle.message("review.details.status.reviewer.wait.for.updates", reviewer)
    ReviewState.NEED_REVIEW -> CollaborationToolsBundle.message("review.details.status.reviewer.need.review", reviewer)
  }

  fun getReviewStateIconBorder(reviewState: ReviewState): Color = when (reviewState) {
    ReviewState.ACCEPTED -> Avatar.Color.ACCEPTED_BORDER
    ReviewState.WAIT_FOR_UPDATES -> Avatar.Color.WAIT_FOR_UPDATES_BORDER
    ReviewState.NEED_REVIEW -> Avatar.Color.NEED_REVIEW_BORDER
  }

  @Suppress("UseDPIAwareInsets")
  val TITLE_GAPS: Insets
    get() = CollaborationToolsUIUtil.getInsets(
      oldUI = Insets(12, 13, 8, 13),
      newUI = Insets(16, 17, 8, 13),
    )

  @Suppress("UseDPIAwareInsets")
  val DESCRIPTION_GAPS: Insets
    get() = CollaborationToolsUIUtil.getInsets(
      oldUI = Insets(0, 13, 18, 13),
      newUI = Insets(0, 17, 22, 13),
    )

  @Suppress("UseDPIAwareInsets")
  val COMMIT_POPUP_BRANCHES_GAPS: Insets
    get() = CollaborationToolsUIUtil.getInsets(
      oldUI = Insets(0, 13, 9, 13),
      newUI = Insets(0, 17, 15, 13),
    )

  @Suppress("UseDPIAwareInsets")

  val COMMIT_INFO_GAPS: Insets
    get() = CollaborationToolsUIUtil.getInsets(
      oldUI = Insets(0, 13, 12, 13),
      newUI = Insets(0, 17, 12, 13),
    )

  @Suppress("UseDPIAwareInsets")
  val STATUSES_GAPS: Insets
    get() = CollaborationToolsUIUtil.getInsets(
      oldUI = Insets(6, 13, 10, 13),
      newUI = Insets(6, 17, 10, 13),
    )

  @Suppress("UseDPIAwareInsets")
  val ACTIONS_GAPS: Insets
    get() = CollaborationToolsUIUtil.getInsets(
      oldUI = Insets(0, 11, 15, 13),
      newUI = Insets(0, 15, 18, 13),
    )

  val STATUSES_MAX_HEIGHT: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 143, newUI = 149)
}