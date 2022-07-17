// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistryImpl
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.actionSystem.SplitButtonAction
import java.util.function.Function

internal class RunToolbarExecutorGroupAction(private val group: RunToolbarExecutorGroup) : SplitButtonAction(group), ExecutorRunToolbarAction {
  override val process: RunToolbarProcess
    get() = group.process

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.CONFIGURATION
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isVisible = !e.isActiveProcess() && e.presentation.isVisible

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isVisible = e.presentation.isVisible && checkMainSlotVisibility(it)
      }
    }
  }

  override fun setShortcutSet(shortcutSet: ShortcutSet) {}
}

internal class RunToolbarExecutorGroup(executorGroup: ExecutorGroup<*>,
                                       childConverter: Function<in Executor, out AnAction>, override val process: RunToolbarProcess) :
  ExecutorRunToolbarAction, ExecutorRegistryImpl.ExecutorGroupActionGroup(executorGroup, childConverter) {

  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.RIGHT_FLEXIBLE

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun setShortcutSet(shortcutSet: ShortcutSet) {}

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
   return state == RunToolbarMainSlotState.CONFIGURATION
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isVisible = !e.isActiveProcess()

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isVisible = e.presentation.isVisible && checkMainSlotVisibility(it)
      }
    }
  }
}