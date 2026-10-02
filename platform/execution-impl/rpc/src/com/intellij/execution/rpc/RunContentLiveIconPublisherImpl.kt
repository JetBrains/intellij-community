// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rpc

import com.intellij.execution.ui.RunContentLiveIconPublisher
import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.topics.broadcast

internal class RunContentLiveIconPublisherImpl : RunContentLiveIconPublisher {
  override fun publish(project: Project, toolWindowId: String, alive: Boolean) {
    RPC_SYNC_RUN_TOPIC.broadcast(project, RunContentLiveIconSyncEvent(toolWindowId, alive))
  }
}
