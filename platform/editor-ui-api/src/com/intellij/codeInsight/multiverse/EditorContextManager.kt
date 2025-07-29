// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.messages.Topic
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import java.util.*

interface EditorContextManager {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): EditorContextManager =
      project.service<EditorContextManager>()

    suspend fun getInstanceAsync(project: Project): EditorContextManager =
      project.serviceAsync<EditorContextManager>()

    val topic: Topic<ChangeEventListener> = Topic(ChangeEventListener::class.java)

    @RequiresBackgroundThread
    @RequiresReadLock
    @JvmStatic
    fun getEditorContext(editor: Editor, project: Project): CodeInsightContext {
      if (!isSharedSourceSupportEnabled(project)) {
        return defaultContext()
      }
      val editorContextManager = getInstance(project)
      return editorContextManager.getEditorContexts(editor).mainContext
    }

    @ApiStatus.Internal
    @JvmStatic
    fun getCachedEditorContext(editor: Editor, project: Project): CodeInsightContext? {
      if (!isSharedSourceSupportEnabled(project)) {
        return defaultContext()
      }
      val editorContextManager = getInstance(project)
      return editorContextManager.getCachedEditorContexts(editor)?.mainContext
    }
  }

  /**
   * @return the currently active code insight context for [editor] if it's known, and `null` otherwise.
   * Works fast.
   */
  fun getCachedEditorContexts(editor: Editor): EditorSelectedContexts?

  /**
   * @return the currently active code insight context for [editor].
   * Can be time-consuming because the preferred context might be needed to be inferred
   */
  @RequiresBackgroundThread
  @RequiresReadLock
  fun getEditorContexts(editor: Editor): EditorSelectedContexts

  @RequiresWriteLock
  fun setEditorContext(editor: Editor, contexts: EditorSelectedContexts)

  val eventFlow: Flow<ChangeEvent>

  class ChangeEvent(
    val editor: Editor,
    val newContexts: EditorSelectedContexts,
  )

  interface ChangeEventListener: EventListener {
    fun editorContextsChanged(event: ChangeEvent)
  }
}

@ApiStatus.NonExtendable
interface EditorSelectedContexts {
  val mainContext: CodeInsightContext

  val additionalContexts: Set<CodeInsightContext>

  val allContexts: Set<CodeInsightContext>
    get() = setOf(mainContext) + additionalContexts

  operator fun contains(context: CodeInsightContext): Boolean
}

// todo IJPL-339 get rid of???
class SingleEditorContext(override val mainContext: CodeInsightContext) : EditorSelectedContexts {
  override val additionalContexts: Set<CodeInsightContext>
    get() = emptySet()

  override fun contains(context: CodeInsightContext): Boolean {
    return context == mainContext
  }

  override fun toString(): String = "SingleEditorContext($mainContext)"
}