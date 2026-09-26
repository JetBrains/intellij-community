// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.impl.DebuggerContextListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.xdebugger.XDebugSessionEventsProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class JavaDebugSessionEventsProvider(private val debugProcess: JavaDebugProcess) : XDebugSessionEventsProvider {
  override fun getUiUpdateEventsFlow(): Flow<Unit> {
    val stateManager = debugProcess.debuggerSession.contextManager
    return channelFlow {
      val listener = DebuggerContextListener { _, event ->
        if (event == DebuggerSession.Event.THREADS_REFRESH) {
          trySend(Unit)
        }
      }
      stateManager.addListener(listener)

      awaitClose {
        stateManager.removeListener(listener)
      }
    }.buffer(Channel.CONFLATED)
  }
}