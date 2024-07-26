// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.ui.HotSwapStatusListener
import com.intellij.debugger.ui.HotSwapUI
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.impl.hotswap.*
import kotlinx.coroutines.CoroutineScope

internal class JvmHotSwapProvider(private val debuggerSession: DebuggerSession) : HotSwapProvider<VirtualFile> {
  override fun createChangesCollector(
    session: HotSwapSession<VirtualFile>,
    coroutineScope: CoroutineScope,
    listener: SourceFileChangesListener<VirtualFile>,
  ) = SourceFileChangesCollectorImpl(
    coroutineScope, listener,
    InProjectFilter(session.project),
    SearchScopeFilter(debuggerSession.searchScope),
  )

  override fun performHotSwap(context: DataContext, session: HotSwapSession<VirtualFile>) {
    val project = context.getData(CommonDataKeys.PROJECT) ?: return
    val listener = session.createStatusListener()
    HotSwapUI.getInstance(project).compileAndReload(debuggerSession, HotSwapStatusListenerAdapter(listener),
                                                    *session.getChanges().toTypedArray())
  }
}

private class HotSwapStatusListenerAdapter(private val listener: HotSwapResultListener) : HotSwapStatusListener {
  override fun onSuccess(sessions: MutableList<DebuggerSession>?) {
    listener.onCompleted()
  }

  override fun onFailure(sessions: MutableList<DebuggerSession>?) {
    listener.onFailed()
  }

  override fun onCancel(sessions: MutableList<DebuggerSession>?) {
    listener.onCanceled()
  }
}

private class InProjectFilter(private val project: Project) : SourceFileChangeFilter<VirtualFile> {
  override suspend fun isApplicable(change: VirtualFile): Boolean =
    readAction { ProjectFileIndex.getInstance(project).isInSource(change) }
}
