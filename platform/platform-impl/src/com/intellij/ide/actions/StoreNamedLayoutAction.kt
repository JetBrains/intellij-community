// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class StoreNamedLayoutAction(protected val layoutNameSupplier: () -> @NlsSafe String) : DumbAwareAction() {

  constructor(@NlsSafe layoutName: String) : this({ layoutName })

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ToolWindowDefaultLayoutManager.getInstance().setLayout(layoutNameSupplier(), ToolWindowManagerEx.getInstanceEx(project).getLayout())
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

}
