package com.intellij.execution.multilaunch.statistics

import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MultiLaunchCreationListener : RunManagerListener {
  override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
    val configuration = settings.configuration as? MultiLaunchConfiguration ?: return
    MultiLaunchUsageCollector.logCreated(configuration)
  }
}