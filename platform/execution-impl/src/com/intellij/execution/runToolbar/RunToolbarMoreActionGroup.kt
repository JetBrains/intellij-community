// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil

class RunToolbarMoreActionGroup: DefaultActionGroup() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, java.lang.Boolean.TRUE)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}