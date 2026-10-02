// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rpc

import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * The topic that sends the live state of a Run tool window icon from the backend to the frontend.
 */
@ApiStatus.Internal
val RPC_SYNC_RUN_TOPIC: ProjectRemoteTopic<RunContentLiveIconSyncEvent> =
  ProjectRemoteTopic("SyncRunContentTopic", RunContentLiveIconSyncEvent.serializer())

@ApiStatus.Internal
@Serializable
data class RunContentLiveIconSyncEvent(val toolwindowId: String, val alive: Boolean)
