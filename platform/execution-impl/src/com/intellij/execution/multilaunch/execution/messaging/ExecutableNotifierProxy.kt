package com.intellij.execution.multilaunch.execution.messaging

import com.intellij.execution.multilaunch.execution.MultiLaunchExecutionModel
import com.intellij.execution.ui.RunContentDescriptor
import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.execution.multilaunch.execution.*
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.state.ExecutableSnapshot
import com.intellij.internal.statistic.StructuredIdeActivity
import java.util.concurrent.CancellationException

internal class ExecutableNotifierProxy(
  private val configurationModel: MultiLaunchExecutionModel,
  private val actualExecutable: Executable,
  private val publisher: ExecutionNotifier,
) : Executable(actualExecutable.uniqueId, actualExecutable.name, actualExecutable.icon, actualExecutable.template) {
  override val supportsDebugging get() = actualExecutable.supportsDebugging

  override fun saveAttributes(snapshot: ExecutableSnapshot) {}
  override fun loadAttributes(snapshot: ExecutableSnapshot) {}

  override suspend fun execute(mode: ExecutionMode, activity: StructuredIdeActivity, lifetime: Lifetime): RunContentDescriptor? {
    lifetime.onTerminationIfAlive {
      configurationModel.executables[actualExecutable]?.let { model ->
        if (!model.status.value.isDone()) {
          publisher.afterCancel(configurationModel.configuration, this)
        }
      }
    }
    publisher.beforeExecute(configurationModel.configuration, this)
    var descriptor: RunContentDescriptor? = null
    try {
      publisher.execute(configurationModel.configuration, this)
      descriptor = actualExecutable.execute(mode, activity, lifetime)
      publisher.afterSuccess(configurationModel.configuration, this)
    }
    catch (_: CancellationException) {
      publisher.afterCancel(configurationModel.configuration, this)
    }
    catch (e: Throwable) {
      publisher.afterFail(configurationModel.configuration, this, e)
    }
    finally {
      publisher.afterExecute(configurationModel.configuration, this)
    }
    return descriptor
  }
}