// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.impl.ProjectRunConfigurationConfigurable
import com.intellij.execution.impl.RunConfigurable
import com.intellij.execution.impl.SingleConfigurationConfigurable
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

interface RunToolbarData {
  companion object {
    @JvmField val RUN_TOOLBAR_DATA_KEY: DataKey<RunToolbarData> = DataKey.create("RUN_TOOLBAR_DATA_KEY")
    @JvmField val RUN_TOOLBAR_POPUP_STATE_KEY: DataKey<Boolean> = DataKey.create("RUN_TOOLBAR_POPUP_STATE_KEY")
    @JvmField val RUN_TOOLBAR_MAIN_STATE: DataKey<RunToolbarMainSlotState> = DataKey.create("RUN_TOOLBAR_MAIN_STATE")

    @ApiStatus.Internal
    @JvmField val RUN_TOOLBAR_SUPPRESS_MAIN_SLOT_USER_DATA_KEY = Key<Boolean>("RUN_TOOLBAR_SUPPRESS_MAIN_SLOT_USER_DATA_KEY")

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
  val waitingForAProcesses: WaitingForAProcesses

  fun clear()
}

internal fun RunContentDescriptor.environment(): ExecutionEnvironment? {
  return this.attachedContent?.component?.let {
    ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(DataManager.getInstance().getDataContext(it))
  }
}

internal fun AnActionEvent.runToolbarData(): RunToolbarData? {
  return this.dataContext.runToolbarData()
}

fun DataContext.runToolbarData(): RunToolbarData? {
  return this.getData(RunToolbarData.RUN_TOOLBAR_DATA_KEY)
}

fun AnActionEvent.mainState(): RunToolbarMainSlotState? {
  return this.dataContext.getData(RunToolbarData.RUN_TOOLBAR_MAIN_STATE) ?: this.project?.let {
    if(RunToolbarSlotManager.getInstance(it).mainSlotData == this.runToolbarData()) RunToolbarMainSlotState.CONFIGURATION else null
  }
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

fun RunToolbarData.startWaitingForAProcess(project: Project, settings: RunnerAndConfigurationSettings, executorId: String) {
  RunToolbarSlotManager.getInstance(project).startWaitingForAProcess(this, settings, executorId)
}

internal fun AnActionEvent.setConfiguration(value: RunnerAndConfigurationSettings?) {
  this.runToolbarData()?.configuration = value
}

internal fun DataContext.setConfiguration(value: RunnerAndConfigurationSettings?) {
  runToolbarData()?.configuration = value
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

fun ExecutionEnvironment.getDisplayName(): String? {
  return this.contentToReuse?.displayName
}

fun AnActionEvent.environment(): ExecutionEnvironment? {
  return runToolbarData()?.environment
}

fun ExecutionEnvironment.isRunning(): Boolean? {
  return this.contentToReuse?.processHandler?.let {
    !it.isProcessTerminating && !it.isProcessTerminated
  }
}


fun AnActionEvent.isProcessTerminating(): Boolean {
  return this.environment()?.isProcessTerminating() == true
}

fun ExecutionEnvironment.isProcessTerminating(): Boolean {
  return this.contentToReuse?.processHandler?.isProcessTerminating == true
}

internal fun AnActionEvent.id(): String? {
  return runToolbarData()?.id
}

internal fun ExecutionEnvironment.getRunToolbarProcess(): RunToolbarProcess? {
  return ExecutorGroup.getGroupIfProxy(this.executor)?.let { executorGroup ->
    RunToolbarProcess.getProcesses().firstOrNull {
      it.executorId == executorGroup.id
    }
  } ?: run {
    RunToolbarProcess.getProcesses().firstOrNull {
      it.executorId == this.executor.id
    }
  }
}

internal fun DataContext.editConfiguration() {
  getData(CommonDataKeys.PROJECT)?.let {
    EditConfigurationsDialog(it, createRunConfigurationConfigurable(it, this)).show()
  }
}

internal fun ExecutionEnvironment.showToolWindowTab() {
  ToolWindowManager.getInstance(this.project).getToolWindow(this.contentToReuse?.contentToolWindowId ?: this.executor.id)?.let {
    val contentManager = it.contentManager
    contentManager.contents.firstOrNull { it.executionId == this.executionId }?.let { content ->
      contentManager.setSelectedContent(content)
    }
    it.show()
  }
}

private fun createRunConfigurationConfigurable(project: Project, dataContext: DataContext): RunConfigurable {
  val settings: RunnerAndConfigurationSettings? = getConfiguration(dataContext)

  fun updateActiveConfigurationFromSelected(configurable: Configurable?) {
    configurable?.let {
      if (it is SingleConfigurationConfigurable<*>) {
        dataContext.setConfiguration(it.settings)
      }
    }
  }

  return when {
    project.isDefault -> object : RunConfigurable(project) {
      override fun getSelectedConfiguration(): RunnerAndConfigurationSettings? {
        return settings
      }

      override fun updateActiveConfigurationFromSelected() {
        updateActiveConfigurationFromSelected(getSelectedConfigurable())
      }
    }
    else -> object : ProjectRunConfigurationConfigurable(project) {
      override fun getSelectedConfiguration(): RunnerAndConfigurationSettings? {
        return settings
      }

      override fun updateActiveConfigurationFromSelected() {
       updateActiveConfigurationFromSelected(getSelectedConfigurable())
      }
    }
  }
}