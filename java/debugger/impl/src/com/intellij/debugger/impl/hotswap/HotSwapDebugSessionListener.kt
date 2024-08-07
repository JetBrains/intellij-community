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
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.impl.hotswap.HotSwapResultListener
import com.intellij.xdebugger.impl.hotswap.HotSwapSession
import com.intellij.xdebugger.impl.hotswap.HotSwapSessionManager
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
internal class HotSwapDebugSessionManager(project: Project) : Disposable.Default, XDebuggerManagerListener {
  private val disposables = ConcurrentHashMap<DebuggerSession, Disposable>()
  private val sessions = ConcurrentHashMap<DebuggerSession, HotSwapSession<*>>()

  init {
    project.getMessageBus().connect(this).subscribe(XDebuggerManager.TOPIC, this)
  }

  fun createSessionListenerOrNull(session: DebuggerSession): HotSwapStatusListener? = getSession(session)
    ?.let { HotSwapStatusListenerAdapter(session, it.startHotSwapListening()) }

  internal fun getSession(session: DebuggerSession): HotSwapSession<*>? = sessions[session]

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

  override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
    if (currentSession == null) return
    val (_, hotSwapSession) = sessions.entries.firstOrNull { (session, _) -> session.xDebugSession === currentSession } ?: return
    HotSwapSessionManager.getInstance(currentSession.project).onSessionSelected(hotSwapSession)
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

private class HotSwapStatusListenerAdapter(private val originalSession: DebuggerSession, private val listener: HotSwapResultListener) : HotSwapStatusListener {
  override fun onSuccess(sessions: MutableList<DebuggerSession>?) {
    sessions?.createListeners(originalSession)?.forEach { it.onSuccessfulReload() }
    listener.onSuccessfulReload()
  }

  override fun onFailure(sessions: MutableList<DebuggerSession>?) {
    sessions?.createListeners(originalSession)?.forEach { it.onFinish() }
    listener.onFinish()
  }

  override fun onCancel(sessions: MutableList<DebuggerSession>?) {
    sessions?.createListeners(originalSession)?.forEach { it.onCanceled() }
    listener.onCanceled()
  }

  override fun onNothingToReload(sessions: MutableList<DebuggerSession>?) {
    sessions?.createListeners(originalSession)?.forEach { it.onFinish() }
    listener.onFinish()
  }

  private fun List<DebuggerSession>.createListeners(except: DebuggerSession) = filter { it !== except }
    .mapNotNull { HotSwapDebugSessionManager.getInstance(except.project).getSession(it) }
    .map { it.startHotSwapListening() }
}
