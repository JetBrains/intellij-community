// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.stateExecutionWidget

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.Nls


interface StateWidgetProcess {
  companion object {
    const val ACTION_PREFIX = "StateWidgetProcess_"
    val EP_NAME: ExtensionPointName<StateWidgetProcess> = ExtensionPointName("com.intellij.stateWidgetProcess")

    @JvmStatic
    fun getProcesses(): List<StateWidgetProcess> = EP_NAME.extensionList

    @JvmStatic
    fun generateActionID(executorId: String) = "${ACTION_PREFIX}_$executorId"
  }

  val ID: String
  val executorId: String
  val name: @Nls String
  val actionId: String
}