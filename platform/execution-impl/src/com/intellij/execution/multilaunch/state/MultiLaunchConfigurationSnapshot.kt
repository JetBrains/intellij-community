package com.intellij.execution.multilaunch.state

import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.util.xmlb.annotations.XCollection

class MultiLaunchConfigurationSnapshot : RunConfigurationOptions() {
  @get:XCollection(style = XCollection.Style.v2)
  val rows by list<ExecutableRowSnapshot>()
  var activateToolWindows by property(false)
}