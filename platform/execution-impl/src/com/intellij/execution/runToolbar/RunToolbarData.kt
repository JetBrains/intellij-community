// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.NlsActions
import javax.swing.Icon

interface RunToolbarData {
  companion object {
    var RUN_TOOLBAR_DATA_KEY: DataKey<RunToolbarData> = DataKey.create("RUN_TOOLBAR_DATA_KEY")
    var RUN_TOOLBAR_POPUP_STATE_KEY: DataKey<Boolean> = DataKey.create("RUN_TOOLBAR_POPUP_STATE_KEY")
  }

  val id: String
  var configuration: RunnerAndConfigurationSettings?
  val environment: ExecutionEnvironment?
  val waitingForProcess: MutableSet<String>
}

internal fun AnActionEvent.runToolbarData(): RunToolbarData? {
  return this.dataContext.getData(RunToolbarData.RUN_TOOLBAR_DATA_KEY)
}

fun AnActionEvent.isOpened(): Boolean {
  return this.dataContext.getData(RunToolbarData.RUN_TOOLBAR_POPUP_STATE_KEY) == true
}

fun AnActionEvent.isItRunToolbarMainSlot(): Boolean {
  return this.project?.let { project ->
    runToolbarData()?.let {
      it == RunToolbarSlotManager.getInstance(project).mainSlotData
    } ?: false
  } ?: false
}

internal fun AnActionEvent.isActiveProcess(): Boolean {
  return this.environment() != null
}

internal fun AnActionEvent.addWaitingForAProcess(executorId: String) {
  runToolbarData()?.waitingForProcess?.add(executorId)
}

internal fun AnActionEvent.setConfiguration(value: RunnerAndConfigurationSettings?) {
  runToolbarData()?.configuration = value
}

internal fun AnActionEvent.configuration(): RunnerAndConfigurationSettings? {
  return runToolbarData()?.configuration
}

internal fun AnActionEvent.arrowData(): Pair<Icon, @NlsActions.ActionText String>? {
  val isOpened = this.dataContext.getData(RunToolbarData.RUN_TOOLBAR_POPUP_STATE_KEY)
                 ?: return null
  return when {
    isOpened -> {
      Pair(AllIcons.Toolbar.Collapse, ActionsBundle.message("action.RunToolbarShowHidePopupAction.hide.text"))
    }
    else -> {
      Pair(AllIcons.Toolbar.Expand, ActionsBundle.message("action.RunToolbarShowHidePopupAction.show.text"))
    }
  }
}

fun AnActionEvent.environment(): ExecutionEnvironment? {
  return runToolbarData()?.environment
}

internal fun AnActionEvent.id(): String? {
  return runToolbarData()?.id
}

internal fun ExecutionEnvironment.getRunToolbarProcess(): RunToolbarProcess? {
  return ExecutorGroup.getGroupIfProxy(this.executor)?.let { executorGroup ->
    RunToolbarProcess.getProcesses().firstOrNull{
      it.executorId == executorGroup.id
    }
  } ?: run {
    RunToolbarProcess.getProcesses().firstOrNull{
      it.executorId == this.executor.id
    }
  }
}