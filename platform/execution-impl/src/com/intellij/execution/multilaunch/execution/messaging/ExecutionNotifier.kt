package com.intellij.execution.multilaunch.execution.messaging

import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.executables.Executable

interface ExecutionNotifier {
  /**
   * Called when multi-run execution was just started.
   */
  fun start(configuration: MultiLaunchConfiguration, executables: List<Executable>)
  /**
   * Called when multi-run execution has een canceled.
   */
  fun cancel(configuration: MultiLaunchConfiguration)
  /**
   * Called when all multi-run executables has been executed.
   */
  fun finish(configuration: MultiLaunchConfiguration)
  /**
   * Called when execution of executable is about to start.
   */
  fun beforeExecute(configuration: MultiLaunchConfiguration, executable: Executable)
  /**
   * Called when execution of executable is starting.
   */
  fun execute(configuration: MultiLaunchConfiguration, executable: Executable)
  /**
   * Called when execution of executable finished with any status (fail or success).
   */
  fun afterExecute(configuration: MultiLaunchConfiguration, executable: Executable)
  /**
   * Called when execution of executable succeeded.
   */
  fun afterSuccess(configuration: MultiLaunchConfiguration, executable: Executable)
  /**
   * Called when execution of executable was canceled.
   */
  fun afterCancel(configuration: MultiLaunchConfiguration, executable: Executable)
  /**
   * Called when execution of executable failed.
   */
  fun afterFail(configuration: MultiLaunchConfiguration, executable: Executable, reason: Throwable?)
}

