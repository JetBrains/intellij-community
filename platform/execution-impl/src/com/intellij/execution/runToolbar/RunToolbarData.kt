// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.impl.ProjectRunConfigurationConfigurable
import com.intellij.execution.impl.RunConfigurable
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.Icon

interface RunToolbarData {
  companion object {
    var RUN_TOOLBAR_DATA_KEY: DataKey<RunToolbarData> = DataKey.create("RUN_TOOLBAR_DATA_KEY")
    var RUN_TOOLBAR_POPUP_STATE_KEY: DataKey<Boolean> = DataKey.create("RUN_TOOLBAR_POPUP_STATE_KEY")
    var RUN_TOOLBAR_MAIN_STATE: DataKey<RunToolbarMainSlotState> = DataKey.create("RUN_TOOLBAR_MAIN_STATE")

    internal fun prepareDescription(@Nls text: String, @Nls description: String): @Nls String {
      return HtmlBuilder().append(text)
          .br()
          .append(
            HtmlChunk
              .font(-1)
              .addText(description)
              .wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(JBUI.CurrentTheme.Label.disabledForeground())))).toString()
    }
  }

  val id: String
  var configuration: RunnerAndConfigurationSettings?
  val environment: ExecutionEnvironment?
  val waitingForProcess: MutableSet<String>
}

internal fun AnActionEvent.runToolbarData(): RunToolbarData? {
  return this.dataContext.runToolbarData()
}

internal fun DataContext.runToolbarData(): RunToolbarData? {
  return this.getData(RunToolbarData.RUN_TOOLBAR_DATA_KEY)
}

fun AnActionEvent.mainState(): RunToolbarMainSlotState? {
  return this.dataContext.getData(RunToolbarData.RUN_TOOLBAR_MAIN_STATE)
}

internal fun DataContext.configuration(): RunnerAndConfigurationSettings? {
  return runToolbarData()?.configuration
}

private fun getConfiguration(dataContext: DataContext): RunnerAndConfigurationSettings? {
  return dataContext.configuration()
}

internal fun AnActionEvent.isActiveProcess(): Boolean {
  return this.environment() != null
}

internal fun AnActionEvent.addWaitingForAProcess(executorId: String) {
  runToolbarData()?.waitingForProcess?.add(executorId)
}

internal fun AnActionEvent.setConfiguration(value: RunnerAndConfigurationSettings?) {
  val runToolbarData = runToolbarData()
  runToolbarData?.configuration = value
}

internal fun AnActionEvent.configuration(): RunnerAndConfigurationSettings? {
  return runToolbarData()?.configuration
}

internal fun AnActionEvent.arrowIcon(): Icon? {
  val isOpened = this.dataContext.getData(RunToolbarData.RUN_TOOLBAR_POPUP_STATE_KEY)
                 ?: return null
  return when {
    isOpened -> {
      AllIcons.Toolbar.Collapse
    }
    else -> {
      AllIcons.Toolbar.Expand
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

internal fun DataContext.editConfiguration() {
  getData(CommonDataKeys.PROJECT)?.let {
    EditConfigurationsDialog(it, createRunConfigurationConfigurable(it, getConfiguration(this))).show()
  }
}

private fun createRunConfigurationConfigurable(project: Project, settings: RunnerAndConfigurationSettings?): RunConfigurable {
  return when {
    project.isDefault -> object : RunConfigurable(project) {
      override fun getSelectedConfiguration(): RunnerAndConfigurationSettings? {
        return settings
      }
    }
    else -> object : ProjectRunConfigurationConfigurable(project) {
      override fun getSelectedConfiguration(): RunnerAndConfigurationSettings? {
        return settings
      }
    }
  }
}