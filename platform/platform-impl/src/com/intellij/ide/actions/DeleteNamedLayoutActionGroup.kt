// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager

class DeleteNamedLayoutActionGroup : ActionGroup(), DumbAware {

  private val childrenCache = NamedLayoutListBasedCache<AnAction> { name ->
    if (
      name != ToolWindowDefaultLayoutManager.DEFAULT_LAYOUT_NAME &&
      name != ToolWindowDefaultLayoutManager.getInstance().activeLayoutName
    ) {
      DeleteNamedLayoutAction(name)
    } else {
      null
    }
  }.apply { dependsOnActiveLayout = true }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = childrenCache.getCachedOrUpdatedArray(AnAction.EMPTY_ARRAY)

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

}