// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.stateExecutionWidget

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.JBColor

class StateWidgetRunProcess : StateWidgetProcess {
  override val ID: String = ToolWindowId.RUN
  override val executorId: String = ToolWindowId.RUN
  override val name: String = ExecutionBundle.message("state.widget.run")

  override val actionId: String = "RunToolbarRunProcess"
  override val moreActionGroupName: String = "RunToolbarRunMoreActionGroupName"
  override val moreActionSubGroupName: String = "RunToolbarRunMoreActionSubGroupName"

  override val showInBar: Boolean = true
  override fun rerunAvailable(): Boolean = StateWidgetProcess.isRerunAvailable()

  override val pillColor: JBColor =  JBColor.namedColor("StateWidget.activeBackground", JBColor(0xBAEEBA, 0xBAEEBA))
}