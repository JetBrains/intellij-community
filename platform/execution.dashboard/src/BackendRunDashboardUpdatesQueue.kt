// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal typealias SequentialComputationRequest = suspend () -> Unit

internal class BackendRunDashboardUpdatesQueue(scope: CoroutineScope) {
  private val channel = Channel<SequentialComputationRequest>(Channel.UNLIMITED)

  init {
    scope.launch {
      for (request in channel) {
        request()
      }
    }
  }

  fun submit(block: SequentialComputationRequest) {
    channel.trySend(block)
  }

  fun close() {
    channel.close()
  }
}