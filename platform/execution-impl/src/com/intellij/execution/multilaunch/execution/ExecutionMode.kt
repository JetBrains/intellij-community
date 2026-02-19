package com.intellij.execution.multilaunch.execution

import com.intellij.icons.AllIcons
import javax.swing.Icon

enum class ExecutionMode {
  Run,
  Debug
}

internal fun ExecutionMode.getIcon(): Icon =
  when (this) {
    ExecutionMode.Debug -> AllIcons.Actions.StartDebugger
    ExecutionMode.Run -> AllIcons.Actions.Execute
  }

internal fun ExecutionMode.getText(): String =
  when (this) {
    ExecutionMode.Debug -> "Debug"
    ExecutionMode.Run -> "Run"
  }
