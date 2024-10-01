// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RestoreFactoryDefaultLayoutAction : DumbAwareToggleAction() {

  companion object {
    const val ID = "RestoreFactoryDefaultLayout"
  }

  init {
    templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean =
    ToolWindowDefaultLayoutManager.getInstance().activeLayoutName == ToolWindowDefaultLayoutManager.FACTORY_DEFAULT_LAYOUT_NAME

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    val layoutManager = ToolWindowDefaultLayoutManager.getInstance()
    layoutManager.activeLayoutName = ToolWindowDefaultLayoutManager.FACTORY_DEFAULT_LAYOUT_NAME
    ToolWindowManagerEx.getInstanceEx(project).setLayout(layoutManager.getLayoutCopy())
  }

}
