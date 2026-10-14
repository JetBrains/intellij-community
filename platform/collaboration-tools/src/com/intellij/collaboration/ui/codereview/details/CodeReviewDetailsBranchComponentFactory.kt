// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
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
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingConstants

object CodeReviewDetailsBranchComponentFactory {
  /**
   * Creates the label with the review target and source branches.
   */
  fun create(
    scope: CoroutineScope,
    branchesVm: CodeReviewBranchesViewModel,
  ): JComponent =
    object : JBLabel() {
      // the width shrinks to cut a long branch name, the height does not shrink
      override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)
    }.apply {
      foreground = NamedColorUtil.getInactiveTextColor()
      val branchesText = combine(branchesVm.targetBranch, branchesVm.sourceBranch) { target, source ->
        CollaborationToolsBundle.message("review.details.branch.checkout.remote.ad.label", target, source)
      }
      bindTextIn(scope, branchesText)
      bindTooltipTextIn(scope, branchesText)
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
