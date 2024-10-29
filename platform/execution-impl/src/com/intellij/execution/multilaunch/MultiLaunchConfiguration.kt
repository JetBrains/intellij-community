package com.intellij.execution.multilaunch

import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.impl.statistics.FusAwareRunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.execution.multilaunch.design.MultiLaunchConfigurationEditor
import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.design.toRow
import com.intellij.execution.multilaunch.execution.conditions.Condition
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.execution.executables.TaskExecutableTemplate
import com.intellij.execution.multilaunch.execution.executables.impl.RunConfigurationExecutableManager
import com.intellij.execution.multilaunch.state.MultiLaunchConfigurationSnapshot
import com.intellij.execution.multilaunch.statistics.*
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

class MultiLaunchConfiguration(
  private val project: Project,
  factory: ConfigurationFactory,
  name: String
) : RunConfigurationBase<MultiLaunchConfigurationSnapshot>(project, factory, name), WithoutOwnBeforeRunSteps, FusAwareRunConfiguration {
  companion object {
    val ORIGIN_KEY = Key.create<MultiLaunchCreationOrigin>("ORIGIN")
  }

  val parameters
    get() = getState()

  val descriptors
    get() = state.rows.map { it.toRow(project, this) }

  val origin get() = getUserData(ORIGIN_KEY) ?: MultiLaunchCreationOrigin.EDIT_CONFIGURATIONS

  @ApiStatus.Internal
  override fun getState(executor: Executor, environment: ExecutionEnvironment) = MultiLaunchProfileState(this, project)

  override fun getState() = options as MultiLaunchConfigurationSnapshot

  override fun checkConfiguration() {
    super.checkConfiguration()
    validateRows(descriptors)
  }

  private fun validateRows(rows: List<ExecutableRow>) {
    if (descriptors.isEmpty()) {
      throw MultiLaunchConfigurationError(ExecutionBundle.message("run.configurations.multilaunch.error.at.least.one.task.required"))
    }

    for (i in 0 until rows.count()) {
      val row = rows[i]
      val rowNumber = i + 1
      row.executable
      ?: throw MultiLaunchConfigurationError(rowNumber, ExecutionBundle.message("run.configurations.multilaunch.error.executable.not.found"))
      row.condition?.validate(this, row)
      ?: throw MultiLaunchConfigurationError(rowNumber, ExecutionBundle.message("run.configurations.multilaunch.error.condition.not.found"))
    }
  }

  @ApiStatus.Internal
  override fun getConfigurationEditor() = MultiLaunchConfigurationEditor(project, this)

  override fun getAdditionalUsageData(): MutableList<EventPair<*>> {

    fun createEvent(executable: Executable): ObjectEventData {
      val template = executable.template

      val kind = when(template) {
        is RunConfigurationExecutableManager -> FusExecutableKind.RUN_CONFIGURATION
        is TaskExecutableTemplate -> FusExecutableKind.TASK
        else -> FusExecutableKind.UNKNOWN
      }

      val typeId = when {
        executable is RunConfigurationExecutableManager.RunConfigurationExecutable -> executable.settings.type.id
        template is TaskExecutableTemplate -> template.type
        else -> ""
      }

      return FusExecutable.createData(kind, typeId)
    }

    fun createEvent(condition: Condition): ObjectEventData {
      return FusCondition.createData(condition.template.type)
    }

    fun createEvent(row: ExecutableRow): ObjectEventData {
      return FusExecutionRow.createData(
        createEvent(row.executable ?: throw CantRunException(ExecutionBundle.message("run.configurations.multilaunch.error.missing.stored.executable"))),
        createEvent(row.condition ?: throw CantRunException(ExecutionBundle.message("run.configurations.multilaunch.error.missing.stored.condition"))),
        row.disableDebugging)
    }

    val fusRows = FusExecutableRows.FIELD.with(descriptors.map { createEvent(it) })
    val fusActivateToolWindows = MultiLaunchEventFields.ACTIVATE_TOOL_WINDOWS_FIELD.with(state.activateToolWindows)

    return mutableListOf(fusRows, fusActivateToolWindows)
  }
}