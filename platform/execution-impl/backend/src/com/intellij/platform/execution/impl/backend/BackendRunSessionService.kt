// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.backend

import com.intellij.execution.RunSessionService
import com.intellij.execution.rpc.RunSession
import com.intellij.execution.rpc.RunSessionId
import com.intellij.execution.rpc.RunSessionEvent
import com.intellij.execution.rpc.RunSessionEvent.SessionStarted
import com.intellij.execution.rpc.toDto
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.project.ProjectId
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BackendRunSessionService() : RunSessionService {

  private val runEventFlow = MutableSharedFlow<RunSessionEvent>(extraBufferCapacity = Channel.UNLIMITED)

  override fun storeRunSession(executorEnvironment: ExecutionEnvironment, descriptor: RunContentDescriptor) {
    service<BackendRunSessionServiceCoroutineScope>().storeRunSession(executorEnvironment, descriptor, runEventFlow)
  }

  override suspend fun getRunSession(id: RunSessionId): RunSession? {
    return id.findValue()
  }

  override fun createRunSessionEventsFlow(projectId: ProjectId): Flow<RunSessionEvent> {
    return runEventFlow
  }
}

@ApiStatus.Internal
@Service
class BackendRunSessionServiceCoroutineScope(val cs: CoroutineScope) {

  fun storeRunSession(executorEnvironment: ExecutionEnvironment, descriptor: RunContentDescriptor, runEventFlow: MutableSharedFlow<RunSessionEvent>) {
    val processHandler = descriptor.processHandler ?: return

    val childScope = cs.childScope("RunContentDescriptorChildScope")

    Disposer.register(descriptor) {
      childScope.cancel()
    }

    cs.launch {
      val tabClosedChannel = Channel<Unit>(capacity = 1)
      val runSession = RunSession(
        executorEnvironment.toDto(childScope),
        createProcessHandlerDto(childScope, processHandler),
        tabClosedChannel
      )
      cs.launch(Dispatchers.EDT) {
        tabClosedChannel.consumeEach {
          Disposer.dispose(descriptor)
        }
      }

      val runSessionId = runSession.storeGlobally(childScope)
      runEventFlow.emit(SessionStarted(runSessionId, runSession))
    }
  }
}

@ApiStatus.Internal
fun RunSessionId.findValue(): RunSession? {
  return findValueById(this, type = RunSessionValueIdType)
}

@ApiStatus.Internal
fun RunSession.storeGlobally(coroutineScope: CoroutineScope): RunSessionId {
  return storeValueGlobally(coroutineScope, this, type = RunSessionValueIdType)
}

private object RunSessionValueIdType : BackendValueIdType<RunSessionId, RunSession>(::RunSessionId)
