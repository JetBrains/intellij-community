// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class StoreNewLayoutAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dialog = LayoutNameInputDialog(
      project,
      IdeBundle.message("dialog.new.window.layout.prompt"),
      IdeBundle.message("dialog.new.window.layout.title"),
      IdeBundle.message("button.save"),
      "",
    )
    dialog.show()
    val name = dialog.inputString
    if (name.isNullOrBlank()) {
      return
    }
    ToolWindowDefaultLayoutManager.getInstance().setLayout(name, ToolWindowManagerEx.getInstanceEx(project).getLayout())
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

}
