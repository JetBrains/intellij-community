// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui.resume

import com.intellij.execution.ui.RunWidgetManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup

class RunWidgetMainResumeGroup : DefaultActionGroup() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible =  e.project?.let { project ->
       RunWidgetManager.getInstance(project).isResumeAvailable()
    } ?: false
  }
}