// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.stateExecutionWidget

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess.Companion.generateActionID
import com.intellij.openapi.wm.ToolWindowId

class StateWidgetDebugProcess : StateWidgetProcess {
  override val ID: String = ToolWindowId.DEBUG
  override val executorId: String = ToolWindowId.DEBUG
  override val name: String = ExecutionBundle.message("state.widget.debug")
  override val actionId: String = generateActionID(executorId)
}