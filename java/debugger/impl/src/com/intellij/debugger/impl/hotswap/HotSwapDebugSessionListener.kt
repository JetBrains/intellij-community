// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.ui.HotSwapStatusListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.asDisposable
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.hotswap.HotSwapResultListener
import com.intellij.xdebugger.impl.hotswap.HotSwapSession
import com.intellij.xdebugger.impl.hotswap.HotSwapSessionManager
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

private data class JavaHotSwapSessionEntry(val hotSwapSession: HotSwapSession<*>, val disposable: Disposable)

private class HotSwapManagerInitActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.serviceAsync<HotSwapDebugSessionManager>()
  }
}

@Service(Service.Level.PROJECT)
internal class HotSwapDebugSessionManager(project: Project, cs: CoroutineScope) : XDebuggerManagerListener {
  private val sessions = ConcurrentHashMap<DebuggerSession, JavaHotSwapSessionEntry>()

  init {
    project.getMessageBus().connect(cs).subscribe(XDebuggerManager.TOPIC, this)
  }

  fun createSessionListenerOrNull(session: DebuggerSession): HotSwapStatusListener? {
    val hotSwapSession = findHotSwapSession(session) ?: return null
    return HotSwapStatusListenerAdapter(session, hotSwapSession.startHotSwapListening())
  }

  internal fun findHotSwapSession(session: DebuggerSession): HotSwapSession<*>? = sessions[session]?.hotSwapSession

  override fun processStarted(debugProcess: XDebugProcess) {
    val session = (debugProcess as? JavaDebugProcess)?.debuggerSession ?: return
    if (!Registry.`is`("debugger.hotswap.floating.toolbar")) return
    val disposable = (debugProcess.session as XDebugSessionImpl).coroutineScope.asDisposable()
    val hotSwapSession = HotSwapSessionManager.getInstance(session.project).createSession(JvmHotSwapProvider(session), disposable)
    sessions[session] = JavaHotSwapSessionEntry(hotSwapSession, disposable)
  }

  override fun processStopped(debugProcess: XDebugProcess) {
    val javaDebugSession = (debugProcess as? JavaDebugProcess)?.debuggerSession ?: return
    val sessionEntry = sessions.remove(javaDebugSession) ?: return
    Disposer.dispose(sessionEntry.disposable)
  }

  override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
    if (currentSession == null) return
    val (_, entry) = sessions.entries.firstOrNull { (session, _) -> session.xDebugSession === currentSession } ?: return
    HotSwapSessionManager.getInstance(currentSession.project).onSessionSelected(entry.hotSwapSession)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): HotSwapDebugSessionManager = project.service()
  }
}

private class HotSwapStatusListenerAdapter(private val originalSession: DebuggerSession, private val listener: HotSwapResultListener) : HotSwapStatusListener {
  override fun onSuccess(sessions: MutableList<DebuggerSession>?) {
    sessions?.createListeners(originalSession)?.forEach { it.onSuccessfulReload() }
    listener.onSuccessfulReload()
  }

  override fun onFailure(sessions: MutableList<DebuggerSession>?) {
    sessions?.createListeners(originalSession)?.forEach { it.onFailure() }
    listener.onFailure()
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
    .mapNotNull { HotSwapDebugSessionManager.getInstance(except.project).findHotSwapSession(it) }
    .map { it.startHotSwapListening() }
}
