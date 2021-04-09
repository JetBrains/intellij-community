// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.stateWidget

import com.intellij.execution.segmentedRunDebugWidget.RunToolbarAction
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess

interface ExecutorRunToolbarAction : RunToolbarAction {
  val process: StateWidgetProcess
  override fun getFlexibleType(): RunToolbarAction.FlexibleType {
    return RunToolbarAction.FlexibleType.ExecutorButton
  }
}