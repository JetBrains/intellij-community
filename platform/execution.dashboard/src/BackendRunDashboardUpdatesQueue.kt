// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class BackendRunDashboardUpdatesQueue(scope: CoroutineScope, private val strategy: OverlappingTasksStrategy) {
  private val channel: Channel<Runnable> = when (strategy) {
    OverlappingTasksStrategy.SCHEDULE_FOR_LATER -> Channel(Channel.UNLIMITED)
    OverlappingTasksStrategy.SKIP_NEW -> Channel(Channel.CONFLATED)
  }

  init {
    scope.launch {
      for (request in channel) {
        request.run()
      }
    }
  }

  fun submit(block: Runnable) {
    channel.trySend(block)
  }

  fun close() {
    channel.close()
  }
}

internal enum class OverlappingTasksStrategy {
  SCHEDULE_FOR_LATER,
  SKIP_NEW
}