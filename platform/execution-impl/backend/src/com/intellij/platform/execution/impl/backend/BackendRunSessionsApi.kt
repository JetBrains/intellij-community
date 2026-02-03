// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.backend

import com.intellij.execution.RunSessionService
import com.intellij.execution.rpc.RunSession
import com.intellij.execution.rpc.RunSessionId
import com.intellij.execution.rpc.RunSessionsApi
import com.intellij.execution.rpc.RunSessionEvent
import com.intellij.platform.project.ProjectId
import kotlinx.coroutines.flow.Flow


internal class BackendRunSessionsApi : RunSessionsApi {

  override suspend fun events(projectId: ProjectId): Flow<RunSessionEvent> {
    return RunSessionService.getInstance()?.createRunSessionEventsFlow(projectId) ?: error("No RunSessionService")
  }

  override suspend fun getSession(projectId: ProjectId, runTabId: RunSessionId): RunSession? {
    return RunSessionService.getInstance()?.getRunSession(runTabId) ?: error("No RunSessionService")
  }
}