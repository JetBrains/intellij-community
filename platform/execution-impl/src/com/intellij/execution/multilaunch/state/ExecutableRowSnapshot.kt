package com.intellij.execution.multilaunch.state

import com.intellij.openapi.components.BaseState

class ExecutableRowSnapshot : BaseState() {
  var executable by property<ExecutableSnapshot>()
  var condition by property<ConditionSnapshot>()
  var disableDebugging by property<Boolean>(false) { it == false }
}

