// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.FakeRerunAction
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.AnActionEvent

class StateWidgetRerunAction : FakeRerunAction() {
  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabledAndVisible = event.presentation.isEnabled && event.presentation.isVisible && StateWidgetProcess.isRerunAvailable()
  }

  override fun getEnvironment(event: AnActionEvent): ExecutionEnvironment? {
    event.project?.let { project ->
      val stateWidgetManager = StateWidgetManager.getInstance(project)
      if (stateWidgetManager.getExecutionsCount() == 1 && stateWidgetManager.getActiveProcesses().firstOrNull()?.rerunAvailable() == true) {
        return stateWidgetManager.getActiveExecutionEnvironments().firstOrNull()
      }

    }
    return null
  }

  override fun getDescriptor(event: AnActionEvent): RunContentDescriptor? {
    return getEnvironment(event)?.contentToReuse
  }
}