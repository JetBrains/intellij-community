// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service

import com.intellij.openapi.externalSystem.model.execution.TaskExecutionSettings


fun TaskExecutionSettings.getAllTaskNames(): List<String> {
  return tasksSettings.map { it.name }.toList()
}

fun TaskExecutionSettings.isSameSettings(second: TaskExecutionSettings): Boolean {
  return externalProjectPath == second.externalProjectPath &&
         toCommandLine() == second.toCommandLine()
}
