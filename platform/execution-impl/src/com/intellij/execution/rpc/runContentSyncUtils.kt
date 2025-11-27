// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rpc

import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.broadcast
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val RPC_SYNC_RUN_TOPIC: ProjectRemoteTopic<RunContentLiveIconSyncEvent> =
  ProjectRemoteTopic("SyncRunContentTopic", RunContentLiveIconSyncEvent.serializer())

@ApiStatus.Internal
fun emitLiveIconUpdate(project: Project, toolwindowId: String, alive: Boolean){
  RPC_SYNC_RUN_TOPIC.broadcast(project, RunContentLiveIconSyncEvent(toolwindowId, alive))
}

@ApiStatus.Internal
@Serializable
data class RunContentLiveIconSyncEvent(val toolwindowId: String, val alive: Boolean)