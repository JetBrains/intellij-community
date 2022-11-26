// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.executors.ExecutorGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class RunToolbarAdditionActionsHolder(private val executorGroup: ExecutorGroup<*>, val process: RunToolbarProcess) {
  companion object {
    @JvmStatic
    fun getAdditionActionId(process: RunToolbarProcess) = "${process.moreActionSubGroupName}_additionAction"

    @JvmStatic
    fun getAdditionActionChooserGroupId(process: RunToolbarProcess) = "${process.moreActionSubGroupName}_additionActionChooserGroupId"
  }

  private var selectedAction: AnAction? = null

  val moreActionChooserGroup: RunToolbarChooserAdditionGroup =
    RunToolbarChooserAdditionGroup(executorGroup, process) { ex ->
      object : RunToolbarProcessAction(process, ex) {
        override fun actionPerformed(e: AnActionEvent) {
          super.actionPerformed(e)
          selectedAction = this
        }

        override fun update(e: AnActionEvent) {
          super.update(e)
          e.presentation.isEnabledAndVisible = e.presentation.isEnabled && e.presentation.isVisible
        }
      }
    }

  val additionAction: RunToolbarAdditionAction = RunToolbarAdditionAction(executorGroup, process) { selectedAction }

  init {
    selectedAction = moreActionChooserGroup.getChildren(null)?.getOrNull(0)
  }
}