// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import java.awt.Insets
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

  val STATUSES_MAX_HEIGHT: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 143, newUI = 143)
  val COMMIT_INFO_MAX_HEIGHT: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 100, newUI = 100)
}