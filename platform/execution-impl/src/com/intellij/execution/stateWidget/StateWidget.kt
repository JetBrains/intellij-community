// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.stateWidget

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistryImpl
import com.intellij.execution.segmentedRunDebugWidget.StateWidgetManager.Companion.getInstance
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess
import com.intellij.openapi.actionSystem.AnActionEvent

internal class StateWidget(val executor: Executor, val process: StateWidgetProcess) : ExecutorRegistryImpl.ExecutorAction(executor) {

  override fun displayTextInToolbar(): Boolean {
    return true
  }


  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      if (getInstance(project).getExecutionsCount() == 0) {
        e.presentation.isEnabledAndVisible = true
        super.update(e)
        e.presentation.text = myExecutor.actionName
        return
      }
    }
    e.presentation.isEnabledAndVisible = false
  }
}