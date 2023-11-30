package com.intellij.execution.multilaunch.execution

import com.intellij.icons.AllIcons

enum class ExecutionMode {
  Run,
  Debug
}

fun ExecutionMode.getIcon() =
  when (this) {
    ExecutionMode.Debug -> AllIcons.Actions.StartDebugger
    ExecutionMode.Run -> AllIcons.Actions.Execute
  }

fun ExecutionMode.getText() =
  when (this) {
    ExecutionMode.Debug -> "Debug"
    ExecutionMode.Run -> "Run"
  }
