// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.messages.Topic
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

/**
 * Handles contexts for virtual files and allows running a code insight session with a given [CodeInsightContext].
 */
interface CodeInsightContextManager {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): CodeInsightContextManager = project.service()

    suspend fun getInstanceAsync(project: Project): CodeInsightContextManager = project.serviceAsync()

    val topic: Topic<CodeInsightContextChangeListener> = Topic(CodeInsightContextChangeListener::class.java)
  }

  /**
   * A code insight context is fixed within a single code insight session.
   *
   * Code insight session does not support coroutines because the project state can change while a coroutine is suspended.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun <Result> performCodeInsightSession(context: CodeInsightContext, block: CodeInsightSession.() -> Result): Result

  val currentCodeInsightSession: CodeInsightSession?

  val currentCodeInsightContext: CodeInsightContext
    get() = currentCodeInsightSession?.context ?: defaultContext()

  /**
   * Returns all registered contexts for [file]
   *
   * @see CodeInsightContextProvider
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun getCodeInsightContexts(file: VirtualFile): List<CodeInsightContext>

  @RequiresReadLock
  @RequiresBackgroundThread
  fun getPreferredContext(file: VirtualFile): CodeInsightContext

  @RequiresReadLock
  @RequiresBackgroundThread
  fun getCodeInsightContext(fileViewProvider: FileViewProvider): CodeInsightContext

  /**
   * Tries to assign context of [fileViewProvider] to [context] if it's not yet assigned to something else.
   *
   * @return the context assigned to [fileViewProvider]
   */
  @Internal
  fun getOrSetContext(fileViewProvider: FileViewProvider, context: CodeInsightContext): CodeInsightContext

  /**
   * Subscribe to this flow to listen for context changes.
   * A new emission means all the contexts are invalidated and will be inferred from scratch.
   */
  val changeFlow: Flow<Unit>

  /**
   * Use [com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled] instead.
   */
  @get: Internal
  val isSharedSourceSupportEnabled: Boolean
}

/**
 * Synchronous listener notifying about changes in the codeinsight-context model
 */
interface CodeInsightContextChangeListener : EventListener {
  @RequiresWriteLock
  fun contextsChanged()
}
