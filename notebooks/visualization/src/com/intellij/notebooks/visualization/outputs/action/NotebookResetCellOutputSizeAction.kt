// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.outputs.action

import com.intellij.notebooks.visualization.context.NotebookDataContext
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification.Frontend
import com.intellij.openapi.project.DumbAwareAction

class NotebookResetCellOutputSizeAction private constructor() : DumbAwareAction(), Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val output = e.dataContext.getData(NotebookDataContext.NOTEBOOK_CELL_OUTPUT_DATA_KEY)
    output?.size?.set(output.size.get().copy(resized = false))
  }
}