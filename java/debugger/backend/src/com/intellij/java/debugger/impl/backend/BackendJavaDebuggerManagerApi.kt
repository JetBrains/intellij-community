// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.backend

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerManagerApi
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionDto
import com.intellij.java.debugger.impl.shared.rpc.JavaSessionState
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.models.findValue
import fleet.rpc.core.toRpc
import kotlinx.coroutines.flow.map

internal class BackendJavaDebuggerManagerApi : JavaDebuggerManagerApi {
  override suspend fun getJavaSession(sessionId: XDebugSessionId): JavaDebuggerSessionDto? {
    val xSession = sessionId.findValue() ?: return null
    xSession.sessionInitializedDeferred().await()
    val process = xSession.debugProcess as? JavaDebugProcess ?: return null
    val debuggerSession = process.debuggerSession
    val javaProcess = debuggerSession.process

    val sessionStateFlow = debuggerSession.sessionStateFlow
    return JavaDebuggerSessionDto(
      initialState = getJavaSessionState(debuggerSession, javaProcess),
      stateFlow = sessionStateFlow.map { getJavaSessionState(debuggerSession, javaProcess) }.toRpc(),
    )
  }
}

private fun getJavaSessionState(debuggerSession: DebuggerSession, javaProcess: DebugProcessImpl): JavaSessionState {
  return JavaSessionState(debuggerSession.isAttached, javaProcess.isEvaluationPossible)
}
