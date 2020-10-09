// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.TreeExpander
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EXPAND_ALL
import com.intellij.openapi.actionSystem.PlatformDataKeys.TREE_EXPANDER
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.project.DumbAwareAction

class ExpandAllAction : DumbAwareAction {
  private val getTreeExpander: (DataContext) -> TreeExpander?

  constructor() : super() {
    getTreeExpander = { TREE_EXPANDER.getData(it) }
  }

  constructor(getExpander: (DataContext) -> TreeExpander?) : super() {
    getTreeExpander = getExpander
    copyFrom(this, ACTION_EXPAND_ALL)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val expander = getTreeExpander(event.dataContext) ?: return
    if (expander.canExpand()) expander.expandAll()
  }

  override fun update(event: AnActionEvent) {
    val expander = getTreeExpander(event.dataContext)
    event.presentation.isVisible = expander == null || expander.isExpandAllVisible && expander.isVisible(event)
    event.presentation.isEnabled = expander != null && expander.canExpand()
  }
}
