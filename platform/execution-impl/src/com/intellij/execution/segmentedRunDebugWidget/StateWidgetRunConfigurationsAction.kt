// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.*
import com.intellij.execution.ExecutorRegistryImpl.RunnerHelper
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.execution.compound.SettingsAndEffectiveTarget
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.popup.StateActionGroupPopup
import java.awt.Dimension
import java.util.function.Supplier
import javax.swing.JComponent

class StateWidgetRunConfigurationsAction : RunConfigurationsComboBoxAction() {
  override fun createFinalAction(configuration: RunnerAndConfigurationSettings, project: Project): AnAction {
    val stateWidgetManager = StateWidgetManager.getInstance(project)
    val count = stateWidgetManager.getActiveCount()

    return if (count > 0) {
      FinalActionGroup(configuration, project)
    }
    else {
      super.createFinalAction(configuration, project)
    }

  }

  override fun createActionPopup(context: DataContext, component: JComponent, disposeCallback: Runnable?): ListPopup {
    val group = createPopupActionGroup(component, context)


    val popup = StateActionGroupPopup(myPopupTitle, group, context, false,
                                      shouldShowDisabledActions(), false, true,
                                      disposeCallback, maxRows, preselectCondition, null) { action ->
      if (action is FinalActionGroup) {
        StateWidgetManager.getInstance(action.project)
          .getActiveProcessesBySettings(action.configuration)
          ?.mapNotNull { it?.name }
          ?.joinToString(separator = "|")
      }
      else null
    }
    popup.setMinimumSize(Dimension(minWidth, minHeight))
    return popup
  }

  override fun update(e: AnActionEvent) {
    e.project?.let {
      val count = StateWidgetManager.getInstance(it).getActiveCount()
      if (count > 1) {
        e.presentation.text = ExecutionBundle.message("state.widget.active.processes.text", count)
        e.presentation.icon = AllIcons.Nodes.Project
        return
      }

    }
    super.update(e)
  }

  private class FinalActionGroup(val configuration: RunnerAndConfigurationSettings, val project: Project) : DefaultActionGroup() {
    private val stateWidgetManager = StateWidgetManager.getInstance(project)
    private val executorRegistry = ExecutorRegistry.getInstance()
    private val availableProcesses = StateWidgetProcess.getProcesses()

    init {
      isPopup = true
    }

    private fun prepareGroup(project: Project): MutableList<AnAction> {
      val actions = mutableListOf<AnAction>()

      availableProcesses.forEach { process ->
        if (executorRegistry is ExecutorRegistryImpl) {
          executorRegistry.getExecutorById(process.executorId)?.let {
            val activeTarget = ExecutionTargetManager.getActiveTarget(project)
            val target = SettingsAndEffectiveTarget(configuration.configuration, activeTarget)
            if (RunnerHelper.canRun(project, listOf(target), it)) {
              val action = createAction(process, it)
              actions.add(action)
            }
          }
        }
      }

      return actions
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      var name = Executor.shortenNameIfNeeded(configuration.name)
      if (name.isEmpty()) {
        name = " "
      }
      e.presentation.setText(name, false)
      e.presentation.description = ExecutionBundle.message("select.0.1", configuration.type.configurationTypeDescription, name)
      setConfigurationIcon(e.presentation, configuration, project)
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      return e?.project?.let { prepareGroup(it).toTypedArray() } ?: emptyArray()
    }

    private fun createAction(process: StateWidgetProcess, executor: Executor): AnAction {
      return object : AnAction(Supplier { process.name }, executor.icon), DumbAware {

        override fun actionPerformed(e: AnActionEvent) {
          if (executorRegistry is ExecutorRegistryImpl) {
            if (canRun(process, executor)) {
              RunnerHelper.run(project, configuration.configuration, configuration, e.dataContext, executor)
            }
          }
        }

        override fun update(e: AnActionEvent) {
          if (executorRegistry is ExecutorRegistryImpl) {
            e.presentation.isEnabled = canRun(process, executor)
          }
        }
      }
    }

    private fun canRun(process: StateWidgetProcess, executor: Executor): Boolean {
      if (executorRegistry is ExecutorRegistryImpl) {
        val activeTarget = ExecutionTargetManager.getActiveTarget(project)
        val target = SettingsAndEffectiveTarget(configuration.configuration, activeTarget)

        val activeProcesses = stateWidgetManager.getActiveProcessesBySettings(configuration)

        return (activeProcesses?.contains(process) != true && RunnerHelper.canRun(project, listOf(target), executor))
      }
      return false
    }
  }

}