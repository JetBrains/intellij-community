// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class InlineCompletionContext internal constructor(val editor: Editor) {
  val state = InlineState()

  val isCurrentlyDisplayingInlays: Boolean
    get() = state.elements.any { !it.isEmpty }

  val startOffset: Int?
    get() = state.firstElement?.offset

  val lastOffset: Int?
    get() = state.lastElement?.offset

  val lineToInsert: String
    get() = state.elements.joinToString("") { it.text }

  fun clear() {
    state.clear()
  }

  companion object {
    private val LOG = thisLogger()
    val INLINE_COMPLETION_CONTEXT = Key.create<InlineCompletionContext>("inline.completion.context")

    fun getOrNull(editor: Editor): InlineCompletionContext? = editor.getUserData(INLINE_COMPLETION_CONTEXT)
    internal fun getOrInit(editor: Editor): InlineCompletionContext {
      return editor.getUserData(INLINE_COMPLETION_CONTEXT) ?: InlineCompletionContext(editor).also {
        editor.putUserData(INLINE_COMPLETION_CONTEXT, it)
      }
    }

    internal fun remove(editor: Editor) {
      getOrNull(editor)?.clear()

      editor.putUserData(INLINE_COMPLETION_CONTEXT, null)
        .also { LOG.trace("Remove inline completion context") }
    }

    @Deprecated(
      "Resetting completion context is unsafe now. Use direct get/reset/remove~InlineCompletionContext instead",
      ReplaceWith("getInlineCompletionContextOrNull()"), DeprecationLevel.ERROR
    )
    fun Editor.initOrGetInlineCompletionContext(): InlineCompletionContext {
      return getOrNull(this)!!
    }

    @Deprecated(
      "Use direct InlineCompletionContext.getOrNull instead",
      ReplaceWith("InlineCompletionContext.getOrNull(this)"), DeprecationLevel.ERROR
    )
    fun Editor.getInlineCompletionContextOrNull(): InlineCompletionContext? = getOrNull(this)
  }
}
