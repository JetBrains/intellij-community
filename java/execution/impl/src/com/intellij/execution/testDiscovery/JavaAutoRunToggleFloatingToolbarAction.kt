// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

class JavaAutoRunToggleFloatingToolbarAction: ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return service<JavaAutoRunFloatingToolbarService>().toolbarEnabled
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val service = service<JavaAutoRunFloatingToolbarService>()
    service.toolbarEnabled = state
  }
}