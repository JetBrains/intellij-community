// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistryImpl
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.SplitButtonAction
import java.util.function.Function

internal class RunToolbarExecutorGroupAction(private val group: RunToolbarExecutorGroup) : SplitButtonAction(group), RunToolbarAction, ExecutorRunToolbarAction {
  override val process: RunToolbarProcess
    get() = group.process

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isVisible = e.project?.let {
      !e.isActiveProcess() && e.presentation.isVisible && if(e.isItRunToolbarMainSlot()) e.project?.let {
        !RunToolbarSlotManager.getInstance(it).getState().isActive()
      } ?: false else true

    } ?: false
  }
}

internal class RunToolbarExecutorGroup(executorGroup: ExecutorGroup<*>,
                                childConverter: Function<in Executor, out AnAction>, val process: RunToolbarProcess) :
  RunToolbarAction, ExecutorRegistryImpl.ExecutorGroupActionGroup(executorGroup, childConverter) {


  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isVisible = e.project?.let {
      !e.isActiveProcess() && e.presentation.isVisible && if(e.isItRunToolbarMainSlot()) e.project?.let {
        !RunToolbarSlotManager.getInstance(it).getState().isActive()
      } ?: false else true

    } ?: false

  }
}