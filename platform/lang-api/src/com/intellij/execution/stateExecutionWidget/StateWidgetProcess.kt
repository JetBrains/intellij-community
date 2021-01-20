// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.stateExecutionWidget

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.Nls
import java.util.function.Predicate
import javax.swing.Icon
import kotlin.streams.toList


interface StateWidgetProcess {
  companion object {
    private const val runDebugKey = "ide.new.navbar"
    private const val runDebugRerunAvailable = "ide.new.navbar.rerun.available"

    const val STATE_WIDGET_MORE_ACTION_GROUP = "StateWidgetMoreActionGroup"
    const val STATE_WIDGET_GROUP = "StateWidgetProcessesActionGroup"

    @JvmStatic
    fun isAvailable(): Boolean {
      return Registry.get(runDebugKey).asBoolean()
    }

    fun isRerunAvailable(): Boolean {
      return Registry.get(runDebugRerunAvailable).asBoolean()
    }

    val EP_NAME: ExtensionPointName<StateWidgetProcess> = ExtensionPointName("com.intellij.stateWidgetProcess")

    @JvmStatic
    fun getProcesses(): List<StateWidgetProcess> = EP_NAME.extensionList

    @JvmStatic
    fun getProcessesByExecutorId(executorId: String): List<StateWidgetProcess> {
      return getProcesses().filter { it.executorId == executorId }.toList()
    }


  }

  val ID: String
  val executorId: String
  val name: @Nls String
  val actionId: String
  val moreActionGroupName: String
  val moreActionSubGroupName: String

  val showInBar: Boolean

  fun rerunAvailable(): Boolean = false
  fun getStopIcon(): Icon? = null
}