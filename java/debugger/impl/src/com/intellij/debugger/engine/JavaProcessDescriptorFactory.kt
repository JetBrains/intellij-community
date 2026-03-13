// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.actions.MuteRendererUtils
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionDto
import com.intellij.java.debugger.impl.shared.rpc.JavaSessionState
import com.intellij.platform.debugger.impl.shared.FrontendDescriptorStateManager
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.frame.XDescriptor
import com.intellij.xdebugger.impl.XDebugSessionImpl
import fleet.rpc.core.toRpc
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

internal object JavaProcessDescriptorFactory {
  @JvmStatic
  fun createProcessDescriptor(process: JavaDebugProcess): CompletableFuture<XDescriptor>? {
    val session = process.session as? XDebugSessionImpl ?: return null
    val debuggerSession = process.debuggerSession
    val cs = session.coroutineScope
    return cs.future {
      val javaProcess = debuggerSession.process
      val sessionStateFlow = debuggerSession.sessionStateFlow
      val renderersFlow = MuteRendererUtils.getOrCreateFlow(session.sessionData)
      val descriptor = JavaDebuggerSessionDto(
        initialState = getJavaSessionState(debuggerSession, javaProcess),
        stateFlow = sessionStateFlow.map { getJavaSessionState(debuggerSession, javaProcess) }.toRpc(),
        areRenderersMutedInitial = renderersFlow.value,
        areRenderersMutedFlow = renderersFlow.toRpc(),
      )
      if (!SplitDebuggerMode.isSplitDebugger()) {
        // for actions to work in monolith
        FrontendDescriptorStateManager.getInstance(debuggerSession.project).registerProcessDescriptor(session.id, descriptor, cs)
      }
      descriptor
    }
  }

  private fun getJavaSessionState(debuggerSession: DebuggerSession, javaProcess: DebugProcessImpl): JavaSessionState {
    return JavaSessionState(debuggerSession.isAttached, javaProcess.isEvaluationPossible)
  }
}
