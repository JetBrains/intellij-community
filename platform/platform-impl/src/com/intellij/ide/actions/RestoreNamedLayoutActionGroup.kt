// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager

class RestoreNamedLayoutActionGroup : ActionGroup(), DumbAware {

  private val childrenCache = NamedLayoutListBasedCache<AnAction> {
    if (it != ToolWindowDefaultLayoutManager.DEFAULT_LAYOUT_NAME) {
      RestoreNamedLayoutActionImpl(it)
    } else {
      null
    }
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = childrenCache.getCachedOrUpdatedArray(AnAction.EMPTY_ARRAY)

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  private class RestoreNamedLayoutActionImpl(layoutName: String) : RestoreNamedLayoutAction(layoutName) {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.text = layoutName
    }
  }

}
