package com.intellij.execution.multilaunch.execution.conditions.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.rd.util.launchBackground
import com.intellij.ui.dsl.builder.Row
import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.execution.ExecutionDescriptor
import com.intellij.execution.multilaunch.execution.messaging.DefaultExecutionNotifier
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.conditions.Condition
import com.intellij.execution.multilaunch.execution.conditions.ConditionTemplate
import com.intellij.execution.multilaunch.execution.messaging.ExecutionNotifier
import com.intellij.execution.multilaunch.state.ConditionSnapshot

class ImmediatelyConditionTemplate : ConditionTemplate {
  override val type = "immediately"

  override fun createCondition() = ImmediatelyCondition()

  inner class ImmediatelyCondition : Condition(this) {
    override val text = ExecutionBundle.message("run.configurations.multilaunch.condition.immediately")
    override fun provideEditor(row: Row) = null
    override fun validate(configuration: MultiLaunchConfiguration, row: ExecutableRow) {}
    override fun createExecutionListener(descriptor: ExecutionDescriptor,
                                         mode: ExecutionMode,
                                         lifetime: Lifetime): ExecutionNotifier =
      Listener(descriptor.executable, mode, lifetime)

    override fun saveAttributes(snapshot: ConditionSnapshot) {}
    override fun loadAttributes(snapshot: ConditionSnapshot) {}

    inner class Listener(
      private val executable: Executable,
      private val mode: ExecutionMode,
      private val lifetime: Lifetime
    ) : DefaultExecutionNotifier() {
      override fun start(configuration: MultiLaunchConfiguration, executables: List<Executable>) {
        lifetime.launchBackground {
          executable.execute(mode, lifetime)
        }
      }
    }
  }
}

