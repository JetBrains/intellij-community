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
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import icons.DvcsImplIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionListener
import javax.swing.JComponent
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
      add(statusIcon)
      add(sourceBranch)
    }

    scope.launchNow {
      branchesVm.showBranchesRequests.collectLatest { branches ->
        val (source, target, hasRemoteBranch) = branches
        val point = RelativePoint.getSouthWestOf(panelWithIcon)
        val advertiser = if (!hasRemoteBranch) {
          HintUtil.createAdComponent(CollaborationToolsBundle.message("review.details.branch.cannot.checkout.as.branch"), JBUI.CurrentTheme.Advertiser.border(), SwingConstants.LEFT).apply {
            icon = AllIcons.General.Warning
          }
        }
        else {
          HintUtil.createAdComponent(CollaborationToolsBundle.message("review.details.branch.checkout.remote.ad.label", target, source), JBUI.CurrentTheme.Advertiser.border(), SwingConstants.LEFT)
        }
        val dataContext = SimpleDataContext.builder()
          .setParent(DataManager.getInstance().getDataContext(panelWithIcon))
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

    return panelWithIcon
  }
}
