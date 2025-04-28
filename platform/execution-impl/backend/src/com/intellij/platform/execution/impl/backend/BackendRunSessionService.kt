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
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class BackendRunSessionService(val project: Project, val cs: CoroutineScope) : RunSessionService {
  companion object {
    @JvmField
    @Topic.ProjectLevel
    val RUN_SESSIONS_TOPIC: Topic<RunSessionsListener> =
      Topic("Run tab created", RunSessionsListener::class.java, Topic.BroadcastDirection.TO_PARENT)
  }

  override fun storeRunSession(executorEnvironment: ExecutionEnvironment, descriptor: RunContentDescriptor) {
    cs.launch {
      val runSession = RunSession(
        executorEnvironment.toDto(cs),
        createProcessHandlerDto(cs, descriptor.processHandler!!)
      )
      val runSessionId = storeValueGlobally(cs, runSession, type = RunSessionValueIdType)
      project.messageBus.syncPublisher(RUN_SESSIONS_TOPIC).sessionStarted(runSessionId)
    }
  }

  override suspend fun getRunSession(id: RunSessionId): RunSession? {
    return findValueById(id, type = RunSessionValueIdType)
  }

  override fun createRunSessionEventsFlow(projectId: ProjectId): Flow<RunSessionEvent> {
    val project = projectId.findProject()
    return channelFlow {
      project.messageBus.connect(this).subscribe(RUN_SESSIONS_TOPIC, object: RunSessionsListener {
        override fun sessionStarted(runSessionId: RunSessionId) {
          launch {
            val element = SessionStarted(runSessionId)
            send(element)
          }
        }
      })
      awaitClose()
    }.buffer(Channel.UNLIMITED)
  }
}

internal object RunSessionValueIdType : BackendValueIdType<RunSessionId, RunSession>(::RunSessionId)

@ApiStatus.Internal
interface RunSessionsListener : EventListener {
  fun sessionStarted(runSessionId: RunSessionId) {}
}