// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.ui.HotSwapStatusListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.impl.hotswap.HotSwapResultListener
import com.intellij.xdebugger.impl.hotswap.HotSwapSession
import com.intellij.xdebugger.impl.hotswap.HotSwapSessionManager
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
internal class HotSwapDebugSessionManager {
  private val disposables = ConcurrentHashMap<DebuggerSession, Disposable>()
  private val sessions = ConcurrentHashMap<DebuggerSession, HotSwapSession<*>>()

  fun createSessionListenerOrNull(session: DebuggerSession): HotSwapStatusListener? = sessions[session]
    ?.let { HotSwapStatusListenerAdapter(it.startHotSwapListening()) }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): HotSwapDebugSessionManager = project.service()
  }

  internal fun createSession(session: DebuggerSession) {
    if (!Registry.`is`("debugger.hotswap.floating.toolbar")) return
    val disposable = Disposer.newDisposable()
    disposables[session] = disposable
    val hotSwapSession = HotSwapSessionManager.getInstance(session.project).createSession(JvmHotSwapProvider(session), disposable)
    sessions[session] = hotSwapSession
  }

  internal fun removeSession(session: DebuggerSession) {
    disposables.remove(session)?.let { Disposer.dispose(it) }
    sessions.remove(session)
  }
}


internal class HotSwapDebugSessionListener : DebuggerManagerListener {
  override fun sessionCreated(session: DebuggerSession?) {
    if (session == null) return
    HotSwapDebugSessionManager.getInstance(session.project).createSession(session)
  }

  override fun sessionRemoved(session: DebuggerSession?) {
    if (session == null) return
    HotSwapDebugSessionManager.getInstance(session.project).removeSession(session)
  }
}

private class HotSwapStatusListenerAdapter(private val listener: HotSwapResultListener) : HotSwapStatusListener {
  override fun onSuccess(sessions: MutableList<DebuggerSession>?) {
    listener.onSuccessfulReload()
  }

  override fun onFailure(sessions: MutableList<DebuggerSession>?) {
    listener.onFinish()
  }

  override fun onCancel(sessions: MutableList<DebuggerSession>?) {
    listener.onCanceled()
  }

  override fun onNothingToReload(sessions: MutableList<DebuggerSession>?) {
    listener.onFinish()
  }
}
