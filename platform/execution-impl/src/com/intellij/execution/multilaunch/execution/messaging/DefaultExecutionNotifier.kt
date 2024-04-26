package com.intellij.execution.multilaunch.execution.messaging

import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.executables.Executable

open class DefaultExecutionNotifier : ExecutionNotifier {
  override fun start(configuration: MultiLaunchConfiguration, executables: List<Executable>) {}

  override fun cancel(configuration: MultiLaunchConfiguration) {}

  override fun finish(configuration: MultiLaunchConfiguration) {}

  override fun beforeExecute(configuration: MultiLaunchConfiguration, executable: Executable) {}

  override fun execute(configuration: MultiLaunchConfiguration, executable: Executable) {}

  override fun afterExecute(configuration: MultiLaunchConfiguration, executable: Executable) {}

  override fun afterSuccess(configuration: MultiLaunchConfiguration, executable: Executable) {}

  override fun afterCancel(configuration: MultiLaunchConfiguration, executable: Executable) {}

  override fun afterFail(configuration: MultiLaunchConfiguration, executable: Executable, reason: Throwable?) {}
}