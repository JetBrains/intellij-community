// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.stateExecutionWidget

import com.intellij.openapi.wm.ToolWindowId

class StateWidgetRunProcess : StateWidgetProcess {
  override val ID: String = ToolWindowId.RUN
  override val executorId: String = ToolWindowId.RUN
}