// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.*
import com.intellij.execution.ExecutorRegistryImpl.RunnerHelper
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.execution.compound.SettingsAndEffectiveTarget
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.FakeRerunAction
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.popup.StateActionGroupPopup
import java.awt.Dimension
import java.util.function.Supplier
import javax.swing.JComponent

class StateWidgetRunConfigurationsAction : RunConfigurationsComboBoxAction() {
  override fun createFinalAction(configuration: RunnerAndConfigurationSettings, project: Project): AnAction {
    val stateWidgetManager = StateWidgetManager.getInstance(project)
    val count = stateWidgetManager.getExecutionsCount()

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
                                      shouldShowDisabledActions(), false, false,
                                      disposeCallback, maxRows, preselectCondition, null, false) { action ->
      if (action is FinalActionGroup) {
        StateWidgetManager.getInstance(action.project)
          .getActiveProcessesBySettings(action.configuration)
          ?.joinToString(separator = " | ") { it.name }
      }
      else null
    }
    popup.setMinimumSize(Dimension(minWidth, minHeight))
    return popup
  }

  override fun update(e: AnActionEvent) {
    e.project?.let { project ->
      val stateWidgetManager = StateWidgetManager.getInstance(project)

      val count = stateWidgetManager.getExecutionsCount()
      if (count > 1) {
        e.presentation.text = ExecutionBundle.message("state.widget.active.processes.text", count)
        e.presentation.icon = AllIcons.RunConfigurations.Application
        return
      } else if(count == 1) {
        stateWidgetManager.getActiveExecutionEnvironments().firstOrNull()?.let {
          updatePresentation(it.executionTarget, it.runnerAndConfigurationSettings, project, e.presentation, e.place)
          return
        }
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
      updatePresentation(templatePresentation)
    }

    private fun prepareGroup(project: Project): MutableList<AnAction> {
      val actions = mutableListOf<AnAction>()

      if (executorRegistry is ExecutorRegistryImpl) {
        actions.add(createRerunAction())

        stateWidgetManager.getExecutionBySettings(configuration)?.forEach { execution ->
          stateWidgetManager.getProcessByExecutionId(execution.executionId)?.let { process ->
            actions.add(createStopAction(process, execution))
          }
        }

        availableProcesses.forEach { process ->
          executorRegistry.getExecutorById(process.executorId)?.let { mainExecutor ->
            if (mainExecutor is ExecutorGroup<*>) {
              val group = DefaultActionGroup.createPopupGroup(Supplier { mainExecutor.actionName })
              mainExecutor.childExecutors().forEach { executor ->
                if (canRun(process, executor)) {
                  group.add(createProcessAction(process, executor))
                }
              }

              group.templatePresentation.icon = mainExecutor.icon
              if (group.childrenCount > 0)
                actions.add(group)
            }
            else if (canRun(process, mainExecutor)) {
              actions.add(createProcessAction(process, mainExecutor))
            }
          }
        }
      }

      return actions
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      updatePresentation(e.presentation)
    }

    private fun updatePresentation(presentation: Presentation) {
      var name = Executor.shortenNameIfNeeded(configuration.name)
      if (name.isEmpty()) {
        name = " "
      }
      presentation.setText(name, false)
      presentation.description = ExecutionBundle.message("select.0.1", configuration.type.configurationTypeDescription, name)
      setConfigurationIcon(presentation, configuration, project)
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      return e?.project?.let { prepareGroup(it).toTypedArray() } ?: emptyArray()
    }

    private fun createProcessAction(process: StateWidgetProcess, executor: Executor): AnAction {
      return object : AnAction(Supplier { executor.actionName }, executor.icon), DumbAware {

        override fun actionPerformed(e: AnActionEvent) {
          if (canRun(process, executor)) {
            RunnerHelper.run(project, configuration.configuration, configuration, e.dataContext, executor)
          }
        }

        override fun update(e: AnActionEvent) {
          e.presentation.isEnabled = canRun(process, executor)
        }
      }
    }

    private fun createRerunAction(): AnAction {
      return object : FakeRerunAction(), DumbAware {

        override fun update(event: AnActionEvent) {
          super.update(event)
          event.presentation.text = ExecutionBundle.message("run.dashboard.rerun.action.name")
          event.presentation.isEnabledAndVisible = event.presentation.isEnabled && event.presentation.isVisible && StateWidgetProcess.isRerunAvailable()
        }

        override fun getEnvironment(event: AnActionEvent): ExecutionEnvironment? {
          event.project?.let { project ->
            val stateWidgetManager = StateWidgetManager.getInstance(project)
            val executions = stateWidgetManager.getExecutionBySettings(configuration)
            if(executions?.count() == 1) {
              return executions.firstOrNull()
            }
          }
          return null
        }

        override fun getDescriptor(event: AnActionEvent): RunContentDescriptor? {
          return getEnvironment(event)?.contentToReuse
        }
      }
    }

    private fun createStopAction(process: StateWidgetProcess, environment: ExecutionEnvironment): AnAction {
      return object : AnAction(Supplier {
        ExecutionBundle.message("state.widget.stop.process.action.item.name", environment.executor.actionName)
      },
                               AllIcons.Actions.Suspend), DumbAware {

        override fun actionPerformed(e: AnActionEvent) {

          ExecutionManagerImpl.stopProcess(environment.contentToReuse)
        }
      }
    }

    private fun canRun(process: StateWidgetProcess, executor: Executor): Boolean {
      val activeTarget = ExecutionTargetManager.getActiveTarget(project)
      val target = SettingsAndEffectiveTarget(configuration.configuration, activeTarget)

      val activeProcesses = stateWidgetManager.getActiveProcessesBySettings(configuration)

      val b = activeProcesses?.contains(process) != true || configuration.configuration.isAllowRunningInParallel
      return (b && RunnerHelper.canRun(project, listOf(target), executor))

    }
  }

}