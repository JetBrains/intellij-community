// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.stateWidget

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistryImpl.ExecutorGroupActionGroup
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.execution.segmentedRunDebugWidget.StateWidgetManager.Companion.getInstance
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.util.function.Function

internal class StateWidgetGroup(executorGroup: ExecutorGroup<*>,
                       childConverter: Function<in Executor?, out AnAction?>, myProcess: StateWidgetProcess) : ExecutorGroupActionGroup(executorGroup, childConverter) {


  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      if (getInstance(project).getExecutionsCount() == 0) {
        e.presentation.isEnabledAndVisible = true
        super.update(e)
        e.presentation.text = myExecutorGroup.actionName
        return
      }
    }
    e.presentation.isEnabledAndVisible = false
  }
}