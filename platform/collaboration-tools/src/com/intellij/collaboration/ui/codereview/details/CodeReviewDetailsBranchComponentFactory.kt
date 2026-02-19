// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.collaboration.ui.codereview.list.search.ShowDirection
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindTooltipTextIn
import com.intellij.collaboration.ui.util.popup.PopupItemPresentation
import com.intellij.collaboration.ui.util.popup.SimplePopupItemRenderer
import com.intellij.collaboration.ui.util.popup.showAndAwait
import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import icons.DvcsImplIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

object CodeReviewDetailsBranchComponentFactory {
  private const val BRANCH_ICON_LINK_GAP = 2

  fun create(
    scope: CoroutineScope,
    branchesVm: CodeReviewBranchesViewModel,
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
      bindTooltipTextIn(scope, branchesVm.sourceBranch)
      minimumSize = Dimension(0,0)
    }

    val panelWithIcon = HorizontalListPanel(BRANCH_ICON_LINK_GAP).apply {
      border = JBUI.Borders.empty(CodeReviewDetailsCommitsComponentFactory.VERT_PADDING, 0, CodeReviewDetailsCommitsComponentFactory.VERT_PADDING - 1, 0)
      add(statusIcon)
      add(sourceBranch)
    }

    scope.launchNow {
      branchesVm.showBranchesRequests.collectLatest { (source, target, hasRemoteBranch) ->
        val point = RelativePoint.getSouthWestOf(panelWithIcon)
        val advertiser = if (!hasRemoteBranch) {
          HintUtil.createAdComponent(CollaborationToolsBundle.message("review.details.branch.cannot.checkout.as.branch"), JBUI.CurrentTheme.Advertiser.border(), SwingConstants.LEFT).apply {
            icon = AllIcons.General.Warning
          }
        }
        else {
          HintUtil.createAdComponent(CollaborationToolsBundle.message("review.details.branch.checkout.remote.ad.label", target, source), JBUI.CurrentTheme.Advertiser.border(), SwingConstants.LEFT)
        }
        val actions = buildList {
          add(ReviewAction.Checkout)
          if (branchesVm.canShowInLog) {
            add(ReviewAction.ShowInLog)
          }
          add(ReviewAction.CopyBranchName)
        }
        JBPopupFactory.getInstance().createPopupChooserBuilder(actions)
          .setRenderer(popupActionsRenderer(source, hasRemoteBranch))
          .setAdvertiser(advertiser)
          .setItemChosenCallback { action ->
            return@setItemChosenCallback when (action) {
              is ReviewAction.Checkout -> branchesVm.fetchAndCheckoutRemoteBranch()
              is ReviewAction.ShowInLog -> branchesVm.fetchAndShowInLog()
              is ReviewAction.CopyBranchName -> {
                CopyPasteManager.getInstance().setContents(StringSelection(source))
              }
            }
          }
          .createPopup()
          .showAndAwait(point, ShowDirection.BELOW)
      }
    }

    return panelWithIcon
  }
}

private fun popupActionsRenderer(sourceBranch: String, hasRemoteBranch: Boolean): ListCellRenderer<ReviewAction> {
  return SimplePopupItemRenderer.create { item ->
    when (item) {
      is ReviewAction.Checkout -> PopupItemPresentation.Simple(
        if (hasRemoteBranch) CollaborationToolsBundle.message("review.details.branch.checkout.remote", sourceBranch)
        else CollaborationToolsBundle.message("review.details.branch.checkout.remote.as.detached.head", sourceBranch)
      )
      is ReviewAction.ShowInLog -> PopupItemPresentation.Simple(
        CollaborationToolsBundle.message("review.details.branch.show.remote.in.git.log", sourceBranch)
      )
      is ReviewAction.CopyBranchName -> PopupItemPresentation.Simple(CollaborationToolsBundle.message("review.details.branch.copy.name"))
    }
  }
}

private sealed interface ReviewAction {
  data object Checkout : ReviewAction
  data object ShowInLog : ReviewAction
  data object CopyBranchName : ReviewAction
}
