package com.intellij.execution.multilaunch.design

import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.project.Project
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.conditions.ConditionFactory
import com.intellij.execution.multilaunch.execution.executables.ExecutableFactory
import com.intellij.execution.multilaunch.state.ExecutableRowSnapshot

internal object ExecutableRowFactory {
  fun create(project: Project, configuration: MultiLaunchConfiguration, snapshot: ExecutableRowSnapshot): ExecutableRow {
    val executable = ExecutableFactory.getInstance(project).create(configuration, snapshot.executable ?: throw CantRunException(ExecutionBundle.message("run.configurations.multilaunch.error.missing.stored.executable")))
    val condition = ConditionFactory.getInstance(project).create(snapshot.condition ?: throw CantRunException(ExecutionBundle.message("run.configurations.multilaunch.error.missing.stored.condition")))
    val disableDebugging = snapshot.disableDebugging
    return ExecutableRow(executable, condition, disableDebugging)
  }
}

internal fun ExecutableRowSnapshot.toRow(project: Project, configuration: MultiLaunchConfiguration): ExecutableRow =
  ExecutableRowFactory.create(project, configuration, this)