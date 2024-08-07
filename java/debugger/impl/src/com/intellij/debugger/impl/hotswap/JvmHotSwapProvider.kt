// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.ui.HotSwapUI
import com.intellij.lang.Language
import com.intellij.lang.jvm.JvmMetaLanguage
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
    listener: SourceFileChangesListener,
  ): SourceFileChangesCollector<VirtualFile> {
    val jvmExtensions = Language.findInstance(JvmMetaLanguage::class.java).getMatchingLanguages()
      .mapNotNull { it.associatedFileType?.defaultExtension }
    return SourceFileChangesCollectorImpl(
      coroutineScope, listener,
      FileExtensionFilter(jvmExtensions),
      InProjectFilter(session.project),
      // TODO add another scope check
      //SearchScopeFilter(debuggerSession.searchScope),
    )
  }

  override fun performHotSwap(context: DataContext, session: HotSwapSession<VirtualFile>) {
    val project = context.getData(CommonDataKeys.PROJECT) ?: return
    HotSwapUI.getInstance(project).compileAndReload(debuggerSession, *session.getChanges().toTypedArray())
  }
}

private class InProjectFilter(private val project: Project) : SourceFileChangeFilter<VirtualFile> {
  override suspend fun isApplicable(change: VirtualFile): Boolean =
    readAction { ProjectFileIndex.getInstance(project).isInSource(change) }
}

private class FileExtensionFilter(private val extensions: List<String>) : SourceFileChangeFilter<VirtualFile> {
  override suspend fun isApplicable(change: VirtualFile): Boolean = extensions.contains(change.extension)
}
