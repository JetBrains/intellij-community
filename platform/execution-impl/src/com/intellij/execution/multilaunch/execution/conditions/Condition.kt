package com.intellij.execution.multilaunch.execution.conditions

import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.execution.ExecutionDescriptor
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.messaging.ExecutionNotifier
import com.intellij.execution.multilaunch.state.ConditionSnapshot
import com.intellij.internal.statistic.StructuredIdeActivity
import org.jetbrains.annotations.ApiStatus

abstract class Condition(
  val template: ConditionTemplate
) {
  abstract val text: String

  /**
   * May return null if condition is without configurable parameters.
   */
  abstract fun provideEditor(row: Row): Cell<*>?
  abstract fun validate(configuration: MultiLaunchConfiguration, row: ExecutableRow)
  @ApiStatus.Internal
  abstract fun createExecutionListener(descriptor: ExecutionDescriptor, mode: ExecutionMode, activity: StructuredIdeActivity, lifetime: Lifetime): ExecutionNotifier
  @ApiStatus.Internal
  abstract fun saveAttributes(snapshot: ConditionSnapshot)
  @ApiStatus.Internal
  abstract fun loadAttributes(snapshot: ConditionSnapshot)
}