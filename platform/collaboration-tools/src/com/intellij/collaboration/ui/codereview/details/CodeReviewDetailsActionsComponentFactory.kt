// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.util.bindContent
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.event.ActionListener
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent

object CodeReviewDetailsActionsComponentFactory {
  private const val BUTTONS_GAP = 10

  fun <Reviewer> createActionsForAuthor(
    scope: CoroutineScope,
    reviewState: Flow<ReviewState>,
    requestedReviewers: Flow<List<Reviewer>>,
    reviewActions: CodeReviewActions,
    moreActionsGroup: DefaultActionGroup
  ): JComponent {
    val requestReviewButton = JButton(reviewActions.requestReviewAction).apply {
      isOpaque = false
      bindVisibility(scope, combine(reviewState, requestedReviewers) { reviewState, requestedReviewers ->
        reviewState == ReviewState.NEED_REVIEW || (reviewState == ReviewState.WAIT_FOR_UPDATES && requestedReviewers.isNotEmpty())
      })
    }
    val reRequestReviewButton = JButton(reviewActions.reRequestReviewAction).apply {
      isOpaque = false
      bindVisibility(scope, combine(reviewState, requestedReviewers) { reviewState, requestedReviewers ->
        reviewState == ReviewState.WAIT_FOR_UPDATES && requestedReviewers.isEmpty()
      })
    }
    val mergeReviewButton = JBOptionButton(
      reviewActions.mergeReviewAction,
      arrayOf(reviewActions.mergeSquashReviewAction, reviewActions.rebaseReviewAction)
    ).apply {
      bindVisibility(scope, reviewState.map { it == ReviewState.ACCEPTED })
    }
    val moreActionsButton = createMoreButton(moreActionsGroup)
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      reviewState.collect { reviewState ->
        moreActionsGroup.removeAll()
        when (reviewState) {
          ReviewState.NEED_REVIEW, ReviewState.WAIT_FOR_UPDATES -> {
            moreActionsGroup.add(createMergeActionGroup(reviewActions))
            moreActionsGroup.add(reviewActions.closeReviewAction.toAnAction())
          }
          ReviewState.ACCEPTED -> {
            moreActionsGroup.add(reviewActions.requestReviewAction.toAnAction())
            moreActionsGroup.add(reviewActions.closeReviewAction.toAnAction())
          }
        }
      }
    }

    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(requestReviewButton)
      add(reRequestReviewButton)
      add(mergeReviewButton)
      add(moreActionsButton)
    }
  }

  fun createActionsForGuest(reviewActions: CodeReviewActions, moreActionsGroup: DefaultActionGroup): JComponent {
    val setMyselfAsReviewerButton = JButton(reviewActions.setMyselfAsReviewerAction).apply {
      isOpaque = false
    }
    val moreActionsButton = createMoreButton(moreActionsGroup)
    moreActionsGroup.apply {
      removeAll()
      add(reviewActions.requestReviewAction.toAnAction())
      add(createMergeActionGroup(reviewActions))
      add(reviewActions.closeReviewAction.toAnAction())
    }

    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(setMyselfAsReviewerButton)
      add(moreActionsButton)
    }
  }

  fun createActionsComponent(
    scope: CoroutineScope,
    requestState: Flow<RequestState>,
    openedStatePanel: JComponent,
    mergedStatePanel: JComponent,
    closedStatePanel: JComponent,
    draftedStatePanel: JComponent
  ): JComponent {
    return Wrapper().apply {
      bindContent(scope, requestState.map { requestState ->
        when (requestState) {
          RequestState.OPENED -> openedStatePanel
          RequestState.MERGED -> mergedStatePanel
          RequestState.CLOSED -> closedStatePanel
          RequestState.DRAFT -> draftedStatePanel
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

  fun createMergeActionGroup(reviewActions: CodeReviewActions): ActionGroup {
    return DefaultActionGroup(CollaborationToolsBundle.message("review.details.action.merge.group"), true).apply {
      add(reviewActions.mergeReviewAction.toAnAction())
      add(reviewActions.mergeSquashReviewAction.toAnAction())
      add(reviewActions.rebaseReviewAction.toAnAction())
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