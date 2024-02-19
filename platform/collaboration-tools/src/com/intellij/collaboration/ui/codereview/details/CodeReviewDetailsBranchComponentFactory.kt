// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.collaboration.ui.codereview.list.search.ShowDirection
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.popup.PopupItemPresentation
import com.intellij.collaboration.ui.util.popup.SimplePopupItemRenderer
import com.intellij.collaboration.ui.util.popup.showAndAwait
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import icons.DvcsImplIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.ListCellRenderer

object CodeReviewDetailsBranchComponentFactory {
  private const val BRANCH_ICON_LINK_GAP = 2

  fun create(
    scope: CoroutineScope,
    branchesVm: CodeReviewBranchesViewModel
  ): JComponent {
    val statusIcon = InlineIconButton(DvcsImplIcons.BranchLabel).apply {
      actionListener = ActionListener { branchesVm.showBranches() }
      scope.launchNow {
        branchesVm.isCheckedOut.collect { isCheckedOut ->
          icon = when {
            !isCheckedOut -> DvcsImplIcons.BranchLabel
            ExperimentalUI.isNewUI() -> DvcsImplIcons.CurrentBranchLabel
            else -> DvcsImplIcons.CurrentBranchFavoriteLabel
          }
        }
      }
    }
    val sourceBranch = ActionLink().apply {
      addActionListener { branchesVm.showBranches() }
      setDropDownLinkIcon()
      bindTextIn(scope, branchesVm.sourceBranch)
    }

    val panelWithIcon = HorizontalListPanel(BRANCH_ICON_LINK_GAP).apply {
      border = JBUI.Borders.empty(CodeReviewDetailsCommitsComponentFactory.VERT_PADDING, 0, CodeReviewDetailsCommitsComponentFactory.VERT_PADDING - 1, 0)
      add(statusIcon)
      add(sourceBranch)
    }

    scope.launchNow {
      branchesVm.showBranchesRequests.collectLatest { (source, target) ->
        val point = RelativePoint.getSouthWestOf(panelWithIcon)
        val actions = buildList {
          add(ReviewAction.Checkout)
          if (branchesVm.canShowInLog) {
            add(ReviewAction.ShowInLog)
          }
        }
        JBPopupFactory.getInstance().createPopupChooserBuilder(actions)
          .setRenderer(popupActionsRenderer(source))
          .setAdText(CollaborationToolsBundle.message("review.details.branch.checkout.remote.ad.label", target, source))
          .setItemChosenCallback { action ->
            return@setItemChosenCallback when (action) {
              is ReviewAction.Checkout -> branchesVm.fetchAndCheckoutRemoteBranch()
              is ReviewAction.ShowInLog -> branchesVm.fetchAndShowInLog()
            }
          }
          .createPopup()
          .showAndAwait(point, ShowDirection.BELOW)
      }
    }

    return panelWithIcon
  }
}

private fun popupActionsRenderer(sourceBranch: String): ListCellRenderer<ReviewAction> {
  return SimplePopupItemRenderer.create { item ->
    when (item) {
      is ReviewAction.Checkout -> PopupItemPresentation.Simple(
        CollaborationToolsBundle.message("review.details.branch.checkout.remote", sourceBranch)
      )
      is ReviewAction.ShowInLog -> PopupItemPresentation.Simple(
        CollaborationToolsBundle.message("review.details.branch.show.remote.in.git.log", sourceBranch)
      )
    }
  }
}

private sealed interface ReviewAction {
  data object Checkout : ReviewAction
  data object ShowInLog : ReviewAction
}
