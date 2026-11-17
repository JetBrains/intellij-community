// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranches
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import java.awt.datatransfer.StringSelection

internal object CodeReviewBranchActionDataKeys {
  val VIEW_MODEL: DataKey<CodeReviewBranchesViewModel> = DataKey.create("Code.Review.Branches.ViewModel")
  val BRANCHES: DataKey<CodeReviewBranches> = DataKey.create("Code.Review.Branches")
}

internal class CodeReviewCheckoutBranchAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val branches = e.getData(CodeReviewBranchActionDataKeys.BRANCHES)
    val viewModel = e.getData(CodeReviewBranchActionDataKeys.VIEW_MODEL)
    e.presentation.isEnabledAndVisible = branches != null && viewModel != null
    e.presentation.isEnabled = viewModel?.isCheckoutInProgress?.value != true
    e.presentation.text = if (branches?.hasRemoteBranch == false) {
      CollaborationToolsBundle.message("review.details.branch.checkout.remote.as.detached.head")
    }
    else {
      templatePresentation.text
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getData(CodeReviewBranchActionDataKeys.VIEW_MODEL)?.fetchAndCheckoutRemoteBranch()
  }
}

internal class CodeReviewCheckoutBranchInNewWorktreeAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val viewModel = e.getData(CodeReviewBranchActionDataKeys.VIEW_MODEL)
    e.presentation.isEnabledAndVisible = viewModel?.canCheckoutInNewWorktree == true
    e.presentation.isEnabled = viewModel?.isCheckoutInProgress?.value != true
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getData(CodeReviewBranchActionDataKeys.VIEW_MODEL)?.checkoutInNewWorktree()
  }
}

internal class CodeReviewShowBranchInLogAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getData(CodeReviewBranchActionDataKeys.VIEW_MODEL)?.canShowInLog == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getData(CodeReviewBranchActionDataKeys.VIEW_MODEL)?.fetchAndShowInLog()
  }
}

internal class CodeReviewCopyBranchNameAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getData(CodeReviewBranchActionDataKeys.BRANCHES) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val branches = e.getData(CodeReviewBranchActionDataKeys.BRANCHES) ?: return
    CopyPasteManager.getInstance().setContents(StringSelection(branches.source))
  }
}