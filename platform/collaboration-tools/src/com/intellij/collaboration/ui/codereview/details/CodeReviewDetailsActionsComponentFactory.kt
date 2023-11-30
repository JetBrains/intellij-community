// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.awt.event.ActionListener
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent

object CodeReviewDetailsActionsComponentFactory {
  private const val BUTTONS_GAP = 10

  fun <Reviewer> createRequestReviewButton(
    scope: CoroutineScope,
    reviewState: Flow<ReviewState>,
    requestedReviewers: Flow<List<Reviewer>>,
    requestReviewAction: Action
  ): JComponent {
    return JButton(requestReviewAction).apply {
      isOpaque = false
      bindVisibilityIn(scope, combine(reviewState, requestedReviewers) { reviewState, requestedReviewers ->
        reviewState == ReviewState.NEED_REVIEW || (reviewState == ReviewState.WAIT_FOR_UPDATES && requestedReviewers.isNotEmpty())
      })
    }
  }

  fun <Reviewer> createReRequestReviewButton(
    scope: CoroutineScope,
    reviewState: Flow<ReviewState>,
    requestedReviewers: Flow<List<Reviewer>>,
    reRequestReviewAction: Action
  ): JComponent {
    return JButton(reRequestReviewAction).apply {
      isOpaque = false
      bindVisibilityIn(scope, combine(reviewState, requestedReviewers) { reviewState, requestedReviewers ->
        reviewState == ReviewState.WAIT_FOR_UPDATES && requestedReviewers.isEmpty()
      })
    }
  }

  fun createActionsForGuest(
    reviewActions: CodeReviewActions,
    moreActionsGroup: DefaultActionGroup,
    mergeActionsCreator: (CodeReviewActions) -> ActionGroup
  ): JComponent {
    val setMyselfAsReviewerButton = JButton(reviewActions.setMyselfAsReviewerAction).apply {
      isOpaque = false
    }
    val moreActionsButton = createMoreButton(moreActionsGroup)
    moreActionsGroup.apply {
      removeAll()
      add(reviewActions.requestReviewAction.toAnAction())
      add(mergeActionsCreator(reviewActions))
      add(reviewActions.closeReviewAction.toAnAction())
    }

    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(setMyselfAsReviewerButton)
      add(moreActionsButton)
    }
  }

  fun createActionsComponent(
    scope: CoroutineScope,
    reviewRequestState: Flow<ReviewRequestState>,
    openedStatePanel: JComponent,
    mergedStatePanel: JComponent,
    closedStatePanel: JComponent,
    draftedStatePanel: JComponent
  ): JComponent {
    return Wrapper().apply {
      bindContentIn(scope, reviewRequestState.map { requestState ->
        when (requestState) {
          ReviewRequestState.OPENED -> openedStatePanel
          ReviewRequestState.MERGED -> mergedStatePanel
          ReviewRequestState.CLOSED -> closedStatePanel
          ReviewRequestState.DRAFT -> draftedStatePanel
        }
      })
    }
  }

  fun createActionsForMergedReview(): JComponent = JBUI.Panels.simplePanel()

  fun createActionsForClosedReview(reopenReviewAction: Action): JComponent {
    val reopenReviewButton = JButton(reopenReviewAction).apply {
      isOpaque = false
    }
    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(reopenReviewButton)
    }
  }

  fun createActionsForDraftReview(postReviewAction: Action): JComponent {
    val postReviewButton = JButton(postReviewAction).apply {
      isOpaque = false
    }
    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(postReviewButton)
    }
  }

  fun createMoreButton(actionGroup: ActionGroup): JComponent {
    return InlineIconButton(AllIcons.Actions.More).apply {
      withBackgroundHover = true
      actionListener = ActionListener { event ->
        val parentComponent = event.source as JComponent
        val popupMenu = ActionManager.getInstance().createActionPopupMenu("Code.Review.Details.Actions.More", actionGroup)
        val point = RelativePoint.getSouthWestOf(parentComponent).originalPoint
        popupMenu.component.show(parentComponent, point.x, point.y + JBUIScale.scale(8))
      }
    }
  }

  data class CodeReviewActions(
    val requestReviewAction: Action,
    val reRequestReviewAction: Action,
    val closeReviewAction: Action,
    val reopenReviewAction: Action,
    val setMyselfAsReviewerAction: Action,
    val postReviewAction: Action,
    val mergeReviewAction: Action,
    val mergeSquashReviewAction: Action,
    val rebaseReviewAction: Action
  )
}