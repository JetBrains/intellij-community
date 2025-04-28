// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.backend

import com.intellij.execution.RunSessionService
import com.intellij.execution.rpc.RunSession
import com.intellij.execution.rpc.RunSessionId
import com.intellij.execution.rpc.RunSessionsApi
import com.intellij.execution.rpc.RunSessionEvent
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.toRpc
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class BackendRunSessionsApi : RunSessionsApi {

  override suspend fun events(projectId: ProjectId): RpcFlow<RunSessionEvent> {
    return RunSessionService.getInstance(projectId.findProject()).createRunSessionEventsFlow(projectId).toRpc()
  }

  override suspend fun getSession(projectId: ProjectId, runTabId: RunSessionId): RunSession? {
    return RunSessionService.getInstance(projectId.findProject()).getRunSession(runTabId)
  }
}