// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service

import com.intellij.openapi.externalSystem.model.execution.TaskExecutionSettings
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.ContainerUtilRt
import java.util.*


fun TaskExecutionSettings.getAllTaskNames(): List<String> {
  val taskNames = ContainerUtilRt.newArrayList<String>()
  for (taskSettings in tasksSettings) {
    taskNames.add(taskSettings.name)
  }
  return ContainerUtil.immutableList(taskNames)
}

fun TaskExecutionSettings.isSameSettings(second: TaskExecutionSettings): Boolean {
  return externalProjectPath == second.externalProjectPath &&
         toCommandLine() == second.toCommandLine()
}

fun StringJoiner.addAll(elements: Iterable<String>) {
  for (element in elements) {
    add(element)
  }
}
