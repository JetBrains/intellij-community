// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.Executor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.project.DumbAware

internal class BaseExecutorActionWrapper(val executor: Executor, val action: AnAction) : AnAction(), DumbAware, UpdateInBackground {
  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    action.update(e)

    e.presentation.text = executor.actionName
  }

  override fun actionPerformed(e: AnActionEvent) {
    action.actionPerformed(e)
  }
}