// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.stateWidget

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistryImpl.ExecutorGroupActionGroup
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.execution.segmentedRunDebugWidget.StateWidgetManager
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class StateWidgetChooserAdditionGroup(val executorGroup: ExecutorGroup<*>, process: StateWidgetProcess,
                                               childConverter: (Executor) -> AnAction) : ExecutorGroupActionGroup(executorGroup,
                                                                                                                  childConverter) {
  var myProcess: StateWidgetProcess? = null

  init {
    myProcess = process
    val presentation = templatePresentation
    presentation.text = executorGroup.getStateWidgetChooserText()
    isPopup = true
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.project?.let {
      e.presentation.isEnabledAndVisible = StateWidgetManager.getInstance(it).getExecutionsCount() == 0
    }
  }
}