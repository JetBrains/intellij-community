package com.intellij.execution.multilaunch.execution.executables

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class TaskExecutableTemplate : ExecutableTemplate {
  companion object {
    val EP_NAME = ExtensionPointName<TaskExecutableTemplate>("com.intellij.multilaunch.task.definition")
  }
}