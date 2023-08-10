// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.JBColor

class RunToolbarRunProcess : RunToolbarProcess {
  override val ID: String = ToolWindowId.RUN
  override val executorId: String = ToolWindowId.RUN
  override val name: String = ExecutionBundle.message("run.toolbar.running")
  override val shortName: String = ExecutionBundle.message("run.toolbar.run")

  override val actionId: String = "RunToolbarRunProcess"
  override val moreActionSubGroupName: String = "RunToolbarRunMoreActionSubGroupName"

  override val showInBar: Boolean = true

  override val pillColor: JBColor = JBColor.namedColor("RunToolbar.Run.activeBackground", JBColor(0xC7FFD1, 0x235423))
}