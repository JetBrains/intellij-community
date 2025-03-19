// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Insets
import javax.swing.Icon
import javax.swing.JComponent

object ReviewDetailsUIUtil {
  private const val TITLE_PANEL_GAP: Int = 8

  fun createTitlePanel(title: JComponent, timelineLink: JComponent): JComponent =
    VerticalListPanel(TITLE_PANEL_GAP).apply {
      add(title)
      add(timelineLink)
    }

  fun getRequestStateText(state: ReviewRequestState): @NlsSafe String = when (state) {
    ReviewRequestState.OPENED -> CollaborationToolsBundle.message("review.details.review.state.open")
    ReviewRequestState.CLOSED -> CollaborationToolsBundle.message("review.details.review.state.closed")
    ReviewRequestState.MERGED -> CollaborationToolsBundle.message("review.details.review.state.merged")
    ReviewRequestState.DRAFT -> CollaborationToolsBundle.message("review.details.review.state.draft")
  }

  fun getReviewStateIcon(reviewState: ReviewState): Icon = when (reviewState) {
    ReviewState.ACCEPTED -> if (ExperimentalUI.isNewUI()) AllIcons.Status.Success else AllIcons.RunConfigurations.TestPassed
    ReviewState.WAIT_FOR_UPDATES -> if (ExperimentalUI.isNewUI()) AllIcons.General.Error else AllIcons.RunConfigurations.TestError
    ReviewState.NEED_REVIEW -> if (ExperimentalUI.isNewUI()) AllIcons.General.Warning else AllIcons.RunConfigurations.TestFailed
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

  private const val OLD_UI_LEFT_GAP = 14
  private const val NEW_UI_LEFT_GAP = 16
  private const val RIGHT_GAP = 14

  @Suppress("UseDPIAwareInsets")
  val TITLE_GAPS: Insets
    get() = CollaborationToolsUIUtil.getInsets(
      oldUI = Insets(12, OLD_UI_LEFT_GAP, 8, RIGHT_GAP),
      newUI = Insets(16, NEW_UI_LEFT_GAP, 16, RIGHT_GAP),
    )

  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("Description should not be shown in details panel")
  @Deprecated("Description should not be shown in details panel")
  @Suppress("UseDPIAwareInsets")
  val DESCRIPTION_GAPS: Insets
    get() = CollaborationToolsUIUtil.getInsets(
      oldUI = Insets(0, OLD_UI_LEFT_GAP, 18, RIGHT_GAP),
      newUI = Insets(0, NEW_UI_LEFT_GAP, 22, RIGHT_GAP),
    )

  @Suppress("UseDPIAwareInsets")
  val COMMIT_POPUP_BRANCHES_GAPS: Insets
    get() = CollaborationToolsUIUtil.getInsets(
      oldUI = Insets(0, OLD_UI_LEFT_GAP, 0, RIGHT_GAP),
      newUI = Insets(0, NEW_UI_LEFT_GAP, 4, RIGHT_GAP),
    )

  @Suppress("UseDPIAwareInsets")

  val COMMIT_INFO_GAPS: Insets
    get() = CollaborationToolsUIUtil.getInsets(
      oldUI = Insets(0, OLD_UI_LEFT_GAP, 12, RIGHT_GAP),
      newUI = Insets(0, NEW_UI_LEFT_GAP, 12, RIGHT_GAP),
    )

  @Suppress("UseDPIAwareInsets")
  val STATUSES_GAPS: Insets
    get() = CollaborationToolsUIUtil.getInsets(
      oldUI = Insets(6, OLD_UI_LEFT_GAP, 10, RIGHT_GAP),
      newUI = Insets(6, NEW_UI_LEFT_GAP, 10, RIGHT_GAP),
    )

  private const val BUTTON_PADDING = 2

  @Suppress("UseDPIAwareInsets")
  val ACTIONS_GAPS: Insets
    get() = CollaborationToolsUIUtil.getInsets(
      oldUI = Insets(0, 11, 15, RIGHT_GAP),
      newUI = Insets(0, NEW_UI_LEFT_GAP - BUTTON_PADDING, 18, RIGHT_GAP),
    )

  val STATUSES_MAX_HEIGHT: Int get() = CollaborationToolsUIUtil.getSize(oldUI = 143, newUI = 149)
}