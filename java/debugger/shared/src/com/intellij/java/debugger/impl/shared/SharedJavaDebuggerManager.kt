// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared

import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerManagerApi
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.impl.FrontendXDebuggerManagerListener
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.rpc.XDebugSessionDto
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class SharedJavaDebuggerManager(private val project: Project, private val cs: CoroutineScope) {
  private val synchronousExecutor = Channel<suspend () -> Unit>(capacity = Integer.MAX_VALUE)
  private val debugSessions = hashMapOf<XDebugSessionId, SharedJavaDebuggerSession>()

  init {
    cs.launch {
      for (event in synchronousExecutor) {
        event()
      }
    }
    cs.launch {
      XDebugManagerProxy.getInstance().getCurrentSessionFlow(project).collectLatest { proxy ->
        if (proxy == null) return@collectLatest
        synchronousExecutor.send {
          val sessionId = proxy.id
          if (sessionId in debugSessions) return@send
          requestJavaSession(sessionId)
        }
      }
    }
    project.messageBus.connect(cs).subscribe(FrontendXDebuggerManagerListener.TOPIC, object : FrontendXDebuggerManagerListener {
      override fun processStopped(sessionId: XDebugSessionId) {
        synchronousExecutor.trySend {
          debugSessions.remove(sessionId)?.close()
        }
      }

      override fun processStarted(sessionId: XDebugSessionId, sessionDto: XDebugSessionDto) {
        synchronousExecutor.trySend {
          requestJavaSession(sessionId)
        }
      }
    })
  }

  private suspend fun requestJavaSession(sessionId: XDebugSessionId) {
    val dto = JavaDebuggerManagerApi.getInstance().getJavaSession(sessionId) ?: return
    val scope = cs.childScope("Java session $sessionId")
    debugSessions.put(sessionId, SharedJavaDebuggerSession(dto, scope))?.close()
  }

  fun getJavaSession(sessionId: XDebugSessionId): SharedJavaDebuggerSession? {
    return debugSessions[sessionId]
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SharedJavaDebuggerManager = project.service()
  }
}
