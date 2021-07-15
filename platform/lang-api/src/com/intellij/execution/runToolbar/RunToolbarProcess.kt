// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBColor
import org.jetbrains.annotations.Nls
import javax.swing.Icon


interface RunToolbarProcess {
  companion object {
    private const val runDebugKey = "ide.new.navbar"

    const val RUN_WIDGET_MORE_ACTION_GROUP = "RunToolbarMoreActionGroup"
    const val RUN_WIDGET_GROUP = "RunToolbarProcessActionGroup"

    const val ACTIVE_STATE_BUTTONS_COUNT = 3

    @JvmStatic
    fun isAvailable(): Boolean {
      return Registry.get(runDebugKey).asBoolean()
    }

    val EP_NAME: ExtensionPointName<RunToolbarProcess> = ExtensionPointName("com.intellij.runToolbarProcess")

    @JvmStatic
    fun getProcesses(): List<RunToolbarProcess> = EP_NAME.extensionList

    @JvmStatic
    fun getProcessesByExecutorId(executorId: String): List<RunToolbarProcess> {
      return getProcesses().filter { it.executorId == executorId }.toList()
    }


  }

  val ID: String
  val executorId: String
  val name: @Nls String

  val actionId: String
  val moreActionSubGroupName: String

  val showInBar: Boolean

  fun rerunAvailable(): Boolean = false
  fun getStopIcon(): Icon? = null

  val pillColor: JBColor
}