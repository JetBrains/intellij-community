// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.rpc.RunSession
import com.intellij.execution.rpc.RunSessionId
import com.intellij.execution.rpc.RunSessionEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.project.ProjectId
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RunSessionService {
  suspend fun getRunSession(id: RunSessionId): RunSession?
  fun storeRunSession(executorEnvironment: ExecutionEnvironment, descriptor: RunContentDescriptor)
  fun createRunSessionEventsFlow(projectId: ProjectId): Flow<RunSessionEvent>

  companion object {
    internal val EP_NAME = ExtensionPointName.create<RunSessionService>("com.intellij.execution.impl.runSessionService")

    @JvmStatic
    fun getInstance(): RunSessionService? {
      return EP_NAME.extensionList.firstOrNull()
    }
  }
}