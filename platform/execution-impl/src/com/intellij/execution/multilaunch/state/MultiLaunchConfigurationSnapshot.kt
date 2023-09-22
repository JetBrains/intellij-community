package com.intellij.execution.multilaunch.state

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.XCollection

class MultiLaunchConfigurationSnapshot : BaseState() {
  @get:XCollection(style = XCollection.Style.v2)
  val rows by list<ExecutableRowSnapshot>()
  var activateToolWindows by property(false)
}