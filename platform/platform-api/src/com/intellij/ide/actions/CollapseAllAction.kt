// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.TreeExpander
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions.ACTION_COLLAPSE_ALL
import com.intellij.openapi.actionSystem.PlatformDataKeys.TREE_EXPANDER
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.ExperimentalUI

class CollapseAllAction : DumbAwareAction {
  private val getTreeExpander: (AnActionEvent) -> TreeExpander?

  constructor() : super() {
    getTreeExpander = { it.getData(TREE_EXPANDER) }
  }

  constructor(getExpander: (AnActionEvent) -> TreeExpander?) : super() {
    getTreeExpander = getExpander
    copyFrom(this, ACTION_COLLAPSE_ALL)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val expander = getTreeExpander(event) ?: return
    if (expander.canCollapse()) expander.collapseAll()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(event: AnActionEvent) {
    val expander = getTreeExpander(event)
    event.presentation.isVisible = expander == null || expander.isCollapseAllVisible && expander.isVisible(event)
    event.presentation.isEnabled = expander != null && expander.canCollapse()
    if (ExperimentalUI.isNewUI() && ActionPlaces.isPopupPlace(event.place)) {
      event.presentation.icon = null
    }
  }
}
