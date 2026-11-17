// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.collaboration.ui.codereview.list.search.ShowDirection
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindTooltipTextIn
import com.intellij.collaboration.ui.util.popup.showAndAwait
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import icons.DvcsImplIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

object CodeReviewDetailsBranchComponentFactory {
  private const val BRANCH_ICON_LINK_GAP = 2

  fun create(
    scope: CoroutineScope,
    branchesVm: CodeReviewBranchesViewModel,
  ): JComponent {
    val statusIcon = JLabel().apply {
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
    val sourceBranch = JBLabel().apply {
      bindTextIn(scope, branchesVm.sourceBranch)
      bindTooltipTextIn(scope, branchesVm.sourceBranch)
      minimumSize = Dimension(0, 0)
    }

    val panelWithIcon = HorizontalListPanel(BRANCH_ICON_LINK_GAP).apply {
      add(statusIcon)
      add(sourceBranch)
    }

    return panelWithIcon
  }

  fun createMoreActionsLink(
    scope: CoroutineScope,
    branchesVm: CodeReviewBranchesViewModel,
  ): JComponent {
    val moreAction = DumbAwareAction.create(AllIcons.Actions.More) {
      branchesVm.showBranches()
    }
    val moreLink = ActionButton(moreAction, null, MORE_ACTIONS_PLACE, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE).apply {
      accessibleContext.accessibleName = CollaborationToolsBundle.message("review.details.more.action")
    }

    scope.launchNow {
      branchesVm.showBranchesRequests.collectLatest { branches ->
        val (source, target, hasRemoteBranch) = branches
        val point = RelativePoint.getSouthWestOf(moreLink)
        val advertiser = if (!hasRemoteBranch) {
          HintUtil.createAdComponent(CollaborationToolsBundle.message("review.details.branch.cannot.checkout.as.branch"), JBUI.CurrentTheme.Advertiser.border(), SwingConstants.LEFT).apply {
            icon = AllIcons.General.Warning
          }
        }
        else {
          HintUtil.createAdComponent(CollaborationToolsBundle.message("review.details.branch.checkout.remote.ad.label", target, source), JBUI.CurrentTheme.Advertiser.border(), SwingConstants.LEFT)
        }
        val dataContext = SimpleDataContext.builder()
          .setParent(DataManager.getInstance().getDataContext(moreLink))
          .add(CodeReviewBranchActionDataKeys.VIEW_MODEL, branchesVm)
          .add(CodeReviewBranchActionDataKeys.BRANCHES, branches)
          .build()
        val actions = ActionManager.getInstance().getAction("CodeReview.Branch.Actions") as ActionGroup
        JBPopupFactory.getInstance().createActionGroupPopup(
          null, actions, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true
        ).apply {
          content.add(advertiser, BorderLayout.SOUTH)
        }.showAndAwait(point, ShowDirection.BELOW)
      }
    }

    return moreLink
  }

  private const val MORE_ACTIONS_PLACE = "CodeReview.Branch.More"
}
