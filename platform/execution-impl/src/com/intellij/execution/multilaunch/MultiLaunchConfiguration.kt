package com.intellij.execution.multilaunch

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.execution.multilaunch.design.MultiLaunchConfigurationEditor
import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.design.toRow
import com.intellij.execution.multilaunch.state.MultiLaunchConfigurationSnapshot

class MultiLaunchConfiguration(
  private val project: Project,
  factory: ConfigurationFactory,
  name: String
) : RunConfigurationMinimalBase<MultiLaunchConfigurationSnapshot>(name, factory, project), WithoutOwnBeforeRunSteps {
  val parameters
    get() = getState()

  val descriptors
    get() = parameters.rows.map { it.toRow(project, this) }

  override fun getState(executor: Executor, environment: ExecutionEnvironment) = MultiLaunchProfileState(this, project)
  override fun getState() = options

  override fun loadState(state: MultiLaunchConfigurationSnapshot) {
    options.rows.clear()
    options.rows.addAll(state.rows)
    options.activateToolWindows = state.activateToolWindows
  }

  override fun clone(): RunConfiguration {
    val clone = MultiLaunchConfiguration(project, factory, name)
    clone.loadState(state)
    return clone
  }

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
      row.executable
        ?: throw MultiLaunchConfigurationError(i + 1, ExecutionBundle.message("run.configurations.multilaunch.error.executable.not.found"))
      row.condition?.validate(this, row)
        ?: throw MultiLaunchConfigurationError(i + 1, ExecutionBundle.message("run.configurations.multilaunch.error.condition.not.found"))
    }
  }

  override fun getConfigurationEditor() = MultiLaunchConfigurationEditor(project, this)
}
