// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.action

import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiscussionsViewModel
import com.intellij.collaboration.ui.codereview.diff.toActionName
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware

abstract class CodeReviewDiscussionsToggleAction : ActionGroup(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val vm = findViewModel(e.dataContext)
    e.presentation.isEnabledAndVisible = vm != null
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val vm = e?.dataContext?.let(::findViewModel) ?: return EMPTY_ARRAY
    return DiscussionsViewOption.entries.map {
      ToggleOptionAction(vm, it)
    }.toTypedArray()
  }

  protected abstract fun findViewModel(ctx: DataContext): CodeReviewDiscussionsViewModel?

  private class ToggleOptionAction(private val vm: CodeReviewDiscussionsViewModel,
                                   private val viewOption: DiscussionsViewOption) : ToggleAction(viewOption.toActionName()) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean {
      return vm.discussionsViewOption.value == viewOption
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      vm.setDiscussionsViewOption(viewOption)
    }
  }
}