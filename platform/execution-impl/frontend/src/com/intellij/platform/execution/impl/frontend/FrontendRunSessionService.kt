// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.frontend

import com.intellij.execution.RunSessionService
import com.intellij.execution.rpc.RunSession
import com.intellij.execution.rpc.RunSessionEvent
import com.intellij.execution.rpc.RunSessionId
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FrontendRunSessionService(val project: Project, val cs: CoroutineScope) : RunSessionService {

  override fun storeRunSession(executorEnvironment: ExecutionEnvironment, descriptor: RunContentDescriptor) {
    throw UnsupportedOperationException("Function should not be called from the frontend")
  }

  override suspend fun getRunSession(id: RunSessionId): RunSession? {
    throw UnsupportedOperationException("Function should not be called from the frontend")
  }

  override fun createRunSessionEventsFlow(projectId: ProjectId): Flow<RunSessionEvent> {
    throw UnsupportedOperationException("Function should not be called from the frontend")
  }
}