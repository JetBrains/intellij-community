// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.stateExecutionWidget

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.executors.ExecutorGroup

abstract class StateWidgetProcessGroup : StateWidgetProcess {
  private val executorRegistry = ExecutorRegistry.getInstance()

  fun getChildExecutorIds(): List<String> {
    executorRegistry.getExecutorById(executorId)?.let { it ->
      if(it is ExecutorGroup<*>) {
        return it.childExecutors().map { it.id }
      }
    }
    return emptyList()
  }
}