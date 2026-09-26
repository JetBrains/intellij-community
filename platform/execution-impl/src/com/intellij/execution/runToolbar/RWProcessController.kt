// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RWProcessController(val project: Project) {
  internal fun getActiveExecutions(): List<ExecutionEnvironment> {
    return ExecutionManagerImpl.getAllDescriptors(project)
      .mapNotNull { it.environment() }
      .filter { isRelevant(it) }
      .filter { it.isRunning() == true }
  }

  private fun isRelevant(environment: ExecutionEnvironment): Boolean {
    return environment.contentToReuse != null && environment.getRunToolbarProcess() != null
  }

}