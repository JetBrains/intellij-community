package com.intellij.execution.multilaunch.execution

import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.execution.ExecutionDescriptor

internal object ExecutionDescriptorFactory {
  fun create(row: ExecutableRow): ExecutionDescriptor? {
    val executable = row.executable ?: return null
    val condition = row.condition ?: return null
    return ExecutionDescriptor(executable, condition, row.disableDebugging)
  }
}

internal fun ExecutableRow.toDescriptor(): ExecutionDescriptor? =
  ExecutionDescriptorFactory.create(this)