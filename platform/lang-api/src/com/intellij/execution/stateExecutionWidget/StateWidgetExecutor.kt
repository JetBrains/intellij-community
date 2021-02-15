// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.stateExecutionWidget

import com.intellij.openapi.extensions.ExtensionPointName

public interface StateWidgetExecutor {
  companion object {
    val STATE_WIDGET_EXECUTOR_EXTENSION_NAME: ExtensionPointName<StateWidgetExecutor> = ExtensionPointName.create(
      "com.intellij.stateWidgetExecutor")
  }

  val ID: String

  val executorID: String
  val baseActionID: String
  val baseToolWindowID: String
  val label: String
}