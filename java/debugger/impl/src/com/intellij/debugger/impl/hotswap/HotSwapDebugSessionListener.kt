// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.impl.hotswap.HotSwapSessionManager
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
internal class HotSwapDebugSessionListener : DebuggerManagerListener {
  private val sessions = ConcurrentHashMap<DebuggerSession, Disposable>()

  override fun sessionCreated(session: DebuggerSession?) {
    if (session == null) return
    if (!Registry.`is`("debugger.hotswap.floating.toolbar")) return
    val disposable = Disposer.newDisposable()
    sessions[session] = disposable
    HotSwapSessionManager.getInstance(session.project).createSession(JvmHotSwapProvider(session), disposable)
  }

  override fun sessionRemoved(session: DebuggerSession?) {
    if (session == null) return
    sessions.remove(session)?.let { Disposer.dispose(it) }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): HotSwapDebugSessionListener = project.service()
  }
}
