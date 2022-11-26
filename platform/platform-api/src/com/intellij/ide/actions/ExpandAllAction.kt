// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.TreeExpander
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EXPAND_ALL
import com.intellij.openapi.actionSystem.PlatformDataKeys.TREE_EXPANDER
import com.intellij.openapi.actionSystem.PlatformDataKeys.TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.ExperimentalUI

class ExpandAllAction : DumbAwareAction {
  private val getTreeExpander: (AnActionEvent) -> TreeExpander?

  constructor() : super() {
    getTreeExpander = { it.getData(TREE_EXPANDER) }
  }

  constructor(getExpander: (AnActionEvent) -> TreeExpander?) : super() {
    getTreeExpander = getExpander
    copyFrom(this, ACTION_EXPAND_ALL)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val expander = getTreeExpander(event) ?: return
    if (expander.canExpand()) expander.expandAll()
  }

  override fun update(event: AnActionEvent) {
    val expander = getTreeExpander(event)
    val hideIfMissing = event.getData(TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER) ?: false
    event.presentation.isVisible = expander == null && !hideIfMissing ||
                                   expander != null && expander.isExpandAllVisible
    event.presentation.isEnabled = expander != null && expander.canExpand()
    if (ExperimentalUI.isNewUI() && ActionPlaces.isPopupPlace(event.place)) {
      event.presentation.icon = null
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}
