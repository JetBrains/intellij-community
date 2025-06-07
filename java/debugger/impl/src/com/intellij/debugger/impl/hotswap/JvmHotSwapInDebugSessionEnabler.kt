// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.ui.HotSwapStatusListener
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.hotswap.HotSwapInDebugSessionEnabler
import com.intellij.xdebugger.hotswap.HotSwapProvider
import com.intellij.xdebugger.hotswap.HotSwapResultListener
import com.intellij.xdebugger.impl.hotswap.HotSwapDebugSessionManager

private class JvmHotSwapInDebugSessionEnabler : HotSwapInDebugSessionEnabler {
  override fun createProvider(process: XDebugProcess): HotSwapProvider<*>? {
    val session = (process as? JavaDebugProcess)?.debuggerSession ?: return null
    return JvmHotSwapProvider(session)
  }
}

internal fun createHotSwapSessionListenerOrNull(session: DebuggerSession): HotSwapStatusListener? {
  val process = session.xDebugSession?.debugProcess ?: return null
  val hotSwapSession = HotSwapDebugSessionManager.getInstance(session.project).findHotSwapSession(process) ?: return null
  return HotSwapStatusListenerAdapter(session, hotSwapSession.startHotSwapListening())
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
    .mapNotNull { HotSwapDebugSessionManager.getInstance(except.project).findHotSwapSession(it.xDebugSession!!.debugProcess) }
    .map { it.startHotSwapListening() }
}
