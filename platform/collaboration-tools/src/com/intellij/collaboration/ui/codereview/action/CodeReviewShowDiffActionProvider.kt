// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.action

import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider

internal sealed class CodeReviewShowDiffActionProvider : AnActionExtensionProvider {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isActive(e: AnActionEvent): Boolean = e.getData(CodeReviewChangeListViewModel.DATA_KEY) != null

  override fun update(e: AnActionEvent) {
    val vm = e.getData(CodeReviewChangeListViewModel.DATA_KEY)
    e.presentation.isEnabled = vm != null && vm.changesSelection.value != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val vm = e.getRequiredData(CodeReviewChangeListViewModel.DATA_KEY)
    vm.doShowDiff()
  }

  abstract fun CodeReviewChangeListViewModel.doShowDiff()

  class Preview : CodeReviewShowDiffActionProvider() {
    override fun CodeReviewChangeListViewModel.doShowDiff() = showDiffPreview()
  }

  class Standalone : CodeReviewShowDiffActionProvider() {
    override fun CodeReviewChangeListViewModel.doShowDiff() = showDiff()
  }
}