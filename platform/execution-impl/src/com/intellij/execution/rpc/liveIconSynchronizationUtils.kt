// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rpc

import com.intellij.openapi.project.Project
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.broadcast
import kotlinx.serialization.Serializable

@Serializable
data class LiveIconEvent(val toolwindowId: String, val alive: Boolean)

val RPC_ICON_TOPIC: ProjectRemoteTopic<LiveIconEvent> = ProjectRemoteTopic("RunDashboardLiveIcon", LiveIconEvent.serializer())

fun emitLiveIconEventIfInBackend(project: Project, toolwindowId: String, alive: Boolean){
  if(!IdeProductMode.isBackend) return
  
  RPC_ICON_TOPIC.broadcast(project, LiveIconEvent(toolwindowId, alive))
}