// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rpc

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.broadcast
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class LiveIconEvent(val toolwindowId: String, val alive: Boolean)

@Serializable
data class OpenToolWindowEvent(val toolwindowId: String, val focus: Boolean, val contentId: RunContentDescriptorIdImpl?)

val RPC_ICON_TOPIC: ProjectRemoteTopic<LiveIconEvent> = ProjectRemoteTopic("RunDashboardLiveIcon", LiveIconEvent.serializer())
val TOOL_WINDOW_OPEN_TOPIC:  ProjectRemoteTopic<OpenToolWindowEvent> = ProjectRemoteTopic("OpenToolWindowEvent", OpenToolWindowEvent.serializer())

@ApiStatus.Internal
fun emitLiveIconEventIfInBackend(project: Project, toolwindowId: String, alive: Boolean){
  if(!IdeProductMode.isBackend) return
  
  RPC_ICON_TOPIC.broadcast(project, LiveIconEvent(toolwindowId, alive))
}

@ApiStatus.Internal
fun emitOpenToolWindowEventIfInBackend(project: Project, toolwindowId: String, focus: Boolean, contentId: RunContentDescriptorIdImpl){
  if(!IdeProductMode.isBackend) return
  TOOL_WINDOW_OPEN_TOPIC.broadcast(project, OpenToolWindowEvent(toolwindowId, focus, contentId))
}