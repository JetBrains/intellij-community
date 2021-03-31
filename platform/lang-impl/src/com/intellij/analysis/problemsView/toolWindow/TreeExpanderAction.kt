// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.ide.TreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformDataKeys.TREE_EXPANDER
import com.intellij.openapi.project.DumbAwareAction

internal class TreeExpandAllAction : TreeExpanderAction(IdeActions.ACTION_EXPAND_ALL) {
  override fun isEnabled(expander: TreeExpander) = expander.canExpand()
  override fun actionPerformed(expander: TreeExpander) = expander.expandAll()
}

internal class TreeCollapseAllAction : TreeExpanderAction(IdeActions.ACTION_COLLAPSE_ALL) {
  override fun isEnabled(expander: TreeExpander) = expander.canCollapse()
  override fun actionPerformed(expander: TreeExpander) = expander.collapseAll()
}

internal abstract class TreeExpanderAction(actionId: String) : DumbAwareAction() {
  init {
    ActionManager.getInstance().getAction(actionId)?.let { copyFrom(it) }
  }

  abstract fun isEnabled(expander: TreeExpander): Boolean
  override fun update(event: AnActionEvent) {
    val expander = event.getData(TREE_EXPANDER)
    event.presentation.isEnabled = expander?.let { isEnabled(it) } ?: false
    event.presentation.isVisible = expander != null
  }

  abstract fun actionPerformed(expander: TreeExpander)
  override fun actionPerformed(event: AnActionEvent) {
    val expander = event.getData(TREE_EXPANDER) ?: return
    if (isEnabled(expander)) actionPerformed(expander)
  }
}
