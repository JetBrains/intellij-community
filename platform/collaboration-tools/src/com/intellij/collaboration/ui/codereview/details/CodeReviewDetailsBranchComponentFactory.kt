// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.action.CodeReviewCheckoutRemoteBranchAction
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.PopupItemPresentation
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil.showAndAwaitListSubmission
import com.intellij.collaboration.ui.util.CodeReviewColorUtil
import com.intellij.collaboration.ui.util.bindIconIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.hover.addHoverAndPressStateListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JLabelUtil
import com.intellij.util.ui.UIUtil
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Cursor
import javax.swing.JComponent
import javax.swing.ListCellRenderer

object CodeReviewDetailsBranchComponentFactory {
  fun create(scope: CoroutineScope,
             branchesVm: CodeReviewBranchesViewModel,
             checkoutAction: AnAction,
             dataContext: DataContext): JComponent {
    val sourceBranch = JBLabel(CollaborationToolsIcons.Review.Branch).apply {
      border = JBUI.Borders.empty(1, 2, 1, 4)
      addHoverAndPressStateListener(comp = this, pressedStateCallback = { branchLabel, isPressed ->
        if (!isPressed) return@addHoverAndPressStateListener
        scope.launch {
          branchLabel as JComponent
          runActionIfSelected(checkoutAction, dataContext, branchesVm.sourceBranch.value, branchesVm.targetBranch.value, branchLabel)
        }
      })
      JLabelUtil.setTrimOverflow(this, true)
      foreground = CurrentBranchComponent.TEXT_COLOR
      bindTextIn(scope, branchesVm.sourceBranch)
      bindIconIn(scope, branchesVm.isCheckedOut.map { isCheckedOut ->
        if (isCheckedOut) CollaborationToolsIcons.Review.BranchCurrent else CollaborationToolsIcons.Review.Branch
      })
    }

    val roundedSourceBranch = RoundedPanel(BorderLayout()).apply {
      UIUtil.setCursor(this, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      border = JBUI.Borders.empty()
      background = CodeReviewColorUtil.Branch.background
      addHoverAndPressStateListener(comp = this, hoveredStateCallback = { component, isHovered ->
        component.background = if (isHovered) CodeReviewColorUtil.Branch.backgroundHovered else CodeReviewColorUtil.Branch.background
      })
      add(sourceBranch, BorderLayout.CENTER)
    }

    return roundedSourceBranch
  }

  private fun popupActionsRenderer(sourceBranch: String): ListCellRenderer<AnAction> {
    return ChooserPopupUtil.createSimpleItemRenderer { anAction ->
      when (anAction) {
        is CodeReviewCheckoutRemoteBranchAction -> PopupItemPresentation.Simple(
          CollaborationToolsBundle.message("review.details.branch.checkout.remote", sourceBranch)
        )
        else -> PopupItemPresentation.ToString(anAction)
      }
    }
  }

  private suspend fun runActionIfSelected(
    checkoutAction: AnAction,
    dataContext: DataContext,
    sourceBranch: String,
    targetBranch: String,
    parentComponent: JComponent
  ) {
    val point = RelativePoint.getSouthWestOf(parentComponent)
    val selectedAction = JBPopupFactory.getInstance().createPopupChooserBuilder(listOf(checkoutAction))
      .setRenderer(popupActionsRenderer(sourceBranch))
      .setAdText(CollaborationToolsBundle.message("review.details.branch.checkout.remote.ad.label", targetBranch, sourceBranch))
      .createPopup()
      .showAndAwaitListSubmission<AnAction>(point)

    if (selectedAction == null) return
    val anActionEvent = AnActionEvent.createFromAnAction(selectedAction, null, "Review.Details.Branch.Checkout", dataContext)
    selectedAction.actionPerformed(anActionEvent)
  }
}