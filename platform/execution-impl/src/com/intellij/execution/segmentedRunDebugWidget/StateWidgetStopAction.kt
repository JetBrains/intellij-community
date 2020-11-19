// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.actions.StopAction
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.AnActionEvent

class StateWidgetStopAction : StopAction() {
  override fun update(e: AnActionEvent) {
    e.project?.let {
      val stateWidgetManager = StateWidgetManager.getInstance(it)
      if(stateWidgetManager.getActiveCount() == 0) {
        e.presentation.isEnabledAndVisible = false
        return
      }
      e.presentation.isEnabledAndVisible = true
    }

    super.update(e)
  }

  override fun getDisplayName(descriptor: RunContentDescriptor?): String {
    return super.getDisplayName(descriptor)
  }
}