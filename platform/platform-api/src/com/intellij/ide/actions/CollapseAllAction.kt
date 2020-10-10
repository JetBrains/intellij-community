// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.TreeExpander
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.IdeActions.ACTION_COLLAPSE_ALL
import com.intellij.openapi.actionSystem.PlatformDataKeys.TOOL_WINDOW
import com.intellij.openapi.actionSystem.PlatformDataKeys.TREE_EXPANDER
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.project.DumbAwareAction

class CollapseAllAction : DumbAwareAction {
  private val getTreeExpander: (AnActionEvent) -> TreeExpander?

  constructor() : super() {
    getTreeExpander = { TREE_EXPANDER.getData(it.dataContext) ?: findTreeExpander(it) }
  }

  constructor(getExpander: (AnActionEvent) -> TreeExpander?) : super() {
    getTreeExpander = getExpander
    copyFrom(this, ACTION_COLLAPSE_ALL)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val expander = getTreeExpander(event) ?: return
    if (expander.canCollapse()) expander.collapseAll()
  }

  override fun update(event: AnActionEvent) {
    val expander = getTreeExpander(event)
    event.presentation.isVisible = expander == null || expander.isCollapseAllVisible && expander.isVisible(event)
    event.presentation.isEnabled = expander != null && expander.canCollapse()
  }

  companion object {
    // find tree expander for a toolbar of a tool window
    internal fun findTreeExpander(event: AnActionEvent): TreeExpander? {
      if (!event.isFromActionToolbar) return null
      val window = TOOL_WINDOW.getData(event.dataContext) ?: return null
      val component = window.contentManagerIfCreated?.selectedContent?.component ?: return null
      val provider = component as? DataProvider ?: return null
      return TREE_EXPANDER.getData(provider)
    }
  }
}
