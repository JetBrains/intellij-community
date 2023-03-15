// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.Avatar
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import com.intellij.collaboration.ui.util.bindIcon
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JLabelUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

object CodeReviewDetailsStatusComponentFactory {
  private const val STATUS_COMPONENT_BORDER = 5
  private const val STATUS_REVIEWER_COMPONENT_GAP = 8
  private const val STATUS_REVIEWER_GAP = 10
  private const val CI_COMPONENTS_GAP = 8
  private const val CI_COMPONENT_BORDER = 5

  fun createConflictsComponent(scope: CoroutineScope, hasConflicts: Flow<Boolean>): JComponent {
    return ReviewDetailsStatusLabel("Code review status: review has conflicts").apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.RunConfigurations.TestError
      text = CollaborationToolsBundle.message("review.details.status.conflicts")
      bindVisibility(scope, hasConflicts)
    }
  }

  fun <T> createNeedReviewerComponent(scope: CoroutineScope, reviewersReview: Flow<Map<T, ReviewState>>): JComponent {
    return ReviewDetailsStatusLabel("Code review status: need reviewer").apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.RunConfigurations.TestError
      text = CollaborationToolsBundle.message("review.details.status.reviewer.missing")
      bindVisibility(scope, reviewersReview.map { it.isEmpty() })
    }
  }

  fun createRequiredReviewsComponent(scope: CoroutineScope, requiredApprovingReviewsCount: Flow<Int>, isDraft: Flow<Boolean>): JComponent {
    return ReviewDetailsStatusLabel("Code review status: required reviews").apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.RunConfigurations.TestError
      bindVisibility(scope, combine(requiredApprovingReviewsCount, isDraft) { requiredApprovingReviewsCount, isDraft ->
        requiredApprovingReviewsCount > 0 && !isDraft
      })
      bindText(scope, requiredApprovingReviewsCount.map { requiredApprovingReviewsCount ->
        CollaborationToolsBundle.message("review.details.status.reviewer.required", requiredApprovingReviewsCount)
      })
    }
  }

  fun createRestrictionComponent(scope: CoroutineScope, isRestricted: Flow<Boolean>, isDraft: Flow<Boolean>): JComponent {
    return ReviewDetailsStatusLabel("Code review status: restricted rights").apply {
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.RunConfigurations.TestError
      text = CollaborationToolsBundle.message("review.details.status.not.authorized.to.merge")
      bindVisibility(scope, combine(isRestricted, isDraft) { isRestricted, isDraft ->
        isRestricted && !isDraft
      })
    }
  }

  fun createCiComponent(scope: CoroutineScope, statusVm: CodeReviewStatusViewModel): JComponent {
    val checkStatus = ReviewDetailsStatusLabel("Code review status: CI status").apply {
      bindIcon(scope, combine(statusVm.pendingCI, statusVm.failedCI) { pendingCI, failedCI ->
        getCheckStatusIcon(pendingCI, failedCI)
      })
      bindText(scope, combine(statusVm.pendingCI, statusVm.failedCI) { pendingCI, failedCI ->
        getCheckStatusText(pendingCI, failedCI)
      })
    }
    val detailsLink = ActionLink(CollaborationToolsBundle.message("review.details.status.ci.link.details")) {
      BrowserUtil.browse(statusVm.urlCI)
    }.apply {
      bindVisibility(scope, combine(statusVm.hasCI, statusVm.pendingCI, statusVm.failedCI) { hasCI, pendingCI, failedCI ->
        hasCI && (pendingCI != 0 || failedCI != 0)
      })
    }

    return HorizontalListPanel(CI_COMPONENTS_GAP).apply {
      name = "Code review status: CI"
      border = JBUI.Borders.empty(CI_COMPONENT_BORDER, 0)
      bindVisibility(scope, statusVm.hasCI)
      add(checkStatus)
      add(detailsLink)
    }
  }

  fun <Reviewer, IconKey> createReviewersReviewStateComponent(
    scope: CoroutineScope,
    reviewersReview: Flow<Map<Reviewer, ReviewState>>,
    reviewerActionProvider: (Reviewer) -> ActionGroup,
    reviewerNameProvider: (Reviewer) -> String,
    avatarKeyProvider: (Reviewer) -> IconKey,
    iconProvider: (iconKey: IconKey, iconSize: Int) -> Icon
  ): JComponent {
    val panel = VerticalListPanel(STATUS_REVIEWER_GAP).apply {
      name = "Code review status: reviewers"
      border = JBUI.Borders.empty(1, 0)
      bindVisibility(scope, reviewersReview.map { it.isNotEmpty() })
    }

    scope.launch {
      reviewersReview.collect { reviewersReview ->
        panel.removeAll()
        reviewersReview.forEach { (reviewer, reviewState) ->
          panel.add(createReviewerReviewStatus(reviewer, reviewState, reviewerActionProvider, reviewerNameProvider, avatarKeyProvider,
                                               iconProvider))
        }
        panel.revalidate()
        panel.repaint()
      }
    }

    return panel
  }

  private fun <Reviewer, IconKey> createReviewerReviewStatus(
    reviewer: Reviewer,
    reviewState: ReviewState,
    reviewerActionProvider: (Reviewer) -> ActionGroup,
    reviewerNameProvider: (Reviewer) -> String,
    avatarKeyProvider: (Reviewer) -> IconKey,
    iconProvider: (iconKey: IconKey, iconSize: Int) -> Icon
  ): JComponent {
    return HorizontalListPanel(STATUS_REVIEWER_COMPONENT_GAP).apply {
      val reviewStatusIconLabel = JLabel().apply {
        icon = ReviewDetailsUIUtil.getReviewStateIcon(reviewState)
      }
      val reviewerLabel = ReviewDetailsStatusLabel("Code review status: reviewer").apply {
        iconTextGap = STATUS_REVIEWER_COMPONENT_GAP
        icon = iconProvider(avatarKeyProvider(reviewer), Avatar.Sizes.BASE)
        text = ReviewDetailsUIUtil.getReviewStateText(reviewState, reviewerNameProvider(reviewer))
      }

      add(reviewStatusIconLabel)
      add(reviewerLabel)

      if (reviewState != ReviewState.ACCEPTED) {
        PopupHandler.installPopupMenu(this, reviewerActionProvider(reviewer), "CodeReviewReviewerStatus")
      }
    }
  }

  private fun getCheckStatusIcon(pendingCI: Int, failedCI: Int): Icon {
    return when {
      pendingCI != 0 && failedCI != 0 -> AllIcons.RunConfigurations.TestCustom
      pendingCI == 0 && failedCI == 0 -> AllIcons.RunConfigurations.TestPassed
      pendingCI != 0 -> AllIcons.RunConfigurations.TestNotRan
      else -> AllIcons.RunConfigurations.TestError
    }
  }

  private fun getCheckStatusText(pendingCI: Int, failedCI: Int): String {
    return when {
      pendingCI != 0 && failedCI != 0 -> CollaborationToolsBundle.message("review.details.status.ci.progress.and.failed")
      pendingCI == 0 && failedCI == 0 -> CollaborationToolsBundle.message("review.details.status.ci.passed")
      pendingCI != 0 -> CollaborationToolsBundle.message("review.details.status.ci.progress")
      else -> CollaborationToolsBundle.message("review.details.status.ci.failed")
    }
  }

  class ReviewDetailsStatusLabel(componentName: String) : JLabel() {
    init {
      name = componentName
      isOpaque = false
      JLabelUtil.setTrimOverflow(this, trim = true)
    }
  }
}