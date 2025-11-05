// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rpc

import com.intellij.openapi.project.Project
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.broadcast
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

val RPC_SYNC_RUN_TOPIC: ProjectRemoteTopic<RunContentSyncEvent> =
  ProjectRemoteTopic("SyncRunContentTopic", RunContentSyncEvent.serializer())

@ApiStatus.Internal
fun emitLiveIconUpdate(project: Project, toolwindowId: String, alive: Boolean){
  if(!IdeProductMode.isBackend) return
  RPC_SYNC_RUN_TOPIC.broadcast(project, RunContentSyncEvent.ShowLiveIcon(toolwindowId, alive))
}

@ApiStatus.Internal
fun emitToolWindowOpen(project: Project, toolwindowId: String, focus: Boolean){
  if(!IdeProductMode.isBackend) return
  RPC_SYNC_RUN_TOPIC.broadcast(project, RunContentSyncEvent.OpenToolWindow(toolwindowId, focus))
}

@ApiStatus.Internal
@Serializable
sealed interface RunContentSyncEvent{
  @Serializable
  data class ShowLiveIcon(val toolwindowId: String, val alive: Boolean) : RunContentSyncEvent

  @Serializable
  data class OpenToolWindow(val toolwindowId: String, val focus: Boolean) : RunContentSyncEvent
}