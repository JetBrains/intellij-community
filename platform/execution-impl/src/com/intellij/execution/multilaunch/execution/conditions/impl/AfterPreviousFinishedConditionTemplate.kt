package com.intellij.execution.multilaunch.execution.conditions.impl

import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.rd.util.launchBackground
import com.intellij.ui.dsl.builder.Row
import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.MultiLaunchConfigurationError
import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.execution.ExecutionDescriptor
import com.intellij.execution.multilaunch.execution.messaging.DefaultExecutionNotifier
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.conditions.Condition
import com.intellij.execution.multilaunch.execution.messaging.ExecutionNotifier
import com.intellij.execution.multilaunch.execution.conditions.ConditionTemplate
import com.intellij.execution.multilaunch.state.ConditionSnapshot

class AfterPreviousFinishedConditionTemplate : ConditionTemplate {
  override val type = "afterPreviousFinished"

  override fun createCondition() = AfterPreviousFinishedCondition()

  inner class AfterPreviousFinishedCondition : Condition(this) {
    override val text = ExecutionBundle.message("run.configurations.multilaunch.condition.after.previous.finished")
    override fun provideEditor(row: Row) = null
    override fun validate(configuration: MultiLaunchConfiguration, row: ExecutableRow) {
      val executable = row.executable ?: return
      if (configuration.descriptors.indexOfFirst { it.executable == executable } == 0) {
        throw MultiLaunchConfigurationError(executable, 1, ExecutionBundle.message("run.configurations.multilaunch.error.condition.cant.be.first.row", text))
      }
    }

    override fun createExecutionListener(descriptor: ExecutionDescriptor,
                                         mode: ExecutionMode,
                                         lifetime: Lifetime): ExecutionNotifier {
      return Listener(descriptor.executable, mode, lifetime)
    }

    override fun saveAttributes(snapshot: ConditionSnapshot) {}

    override fun loadAttributes(snapshot: ConditionSnapshot) {}

    inner class Listener(
      private val targetExecutable: Executable,
      private val mode: ExecutionMode,
      private val lifetime: Lifetime
    ) : DefaultExecutionNotifier() {
      private var previousExecutable: Executable? = null

      override fun start(configuration: MultiLaunchConfiguration, executables: List<Executable>) {
        val currentIndex = executables.indexOf(targetExecutable)
        if (currentIndex <= 0 || currentIndex >= executables.count()) {
          throw CantRunException(ExecutionBundle.message("run.configurations.multilaunch.error.invalid.configuration"))
        }
        previousExecutable = executables[currentIndex - 1]
      }

      override fun afterExecute(configuration: MultiLaunchConfiguration, executable: Executable) {
        if (executable == previousExecutable) {
          lifetime.launchBackground {
            targetExecutable.execute(mode, lifetime)
          }
        }
      }
    }
  }
}