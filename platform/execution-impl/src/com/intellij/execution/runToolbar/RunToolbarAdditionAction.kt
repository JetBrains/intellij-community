// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.ExecutorRegistryImpl
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation

internal class RunToolbarAdditionAction(val executorGroup: ExecutorGroup<*>,
                                        val process: RunToolbarProcess, val selectedAction: () -> AnAction?) : AnAction() {

  init {
    updatePresentation(templatePresentation)
  }

  override fun update(e: AnActionEvent) {
    updatePresentation(e.presentation)

    e.project?.let {
      e.presentation.isEnabled = e.environment() == null
    }
  }

  private fun updatePresentation(presentation: Presentation) {
    val action = selectedAction()
    if (action is ExecutorRegistryImpl.ExecutorAction) {
      presentation.copyFrom(action.getTemplatePresentation())
      presentation.text = executorGroup.getRunToolbarActionText(action.templatePresentation.text)
    }
  }


  override fun actionPerformed(e: AnActionEvent) {
    selectedAction()?.actionPerformed(e)
  }
}