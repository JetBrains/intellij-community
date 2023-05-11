// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class InlineState private constructor(
  var suggestionIndex: Int = 0,
  var lastStartOffset: Int = 0,
  var lastModificationStamp: Long = 0,
  var suggestions: List<InlineCompletionElement> = emptyList(),
) {
  fun init() {
    suggestionIndex = 0
  }

  fun rest() {
    lastModificationStamp = 0
  }

  companion object {
    private val INLINE_COMPLETION_STATE: Key<InlineState> = Key.create("inline.completion.completion.state")

    fun Editor.getInlineCompletionState(): InlineState? = getUserData(INLINE_COMPLETION_STATE)
    fun Editor.initOrGetInlineCompletionState(): InlineState = getInlineCompletionState() ?: InlineState().also { putUserData(INLINE_COMPLETION_STATE, it) }
    fun Editor.resetInlineCompletionState() = putUserData(INLINE_COMPLETION_STATE, null)
  }
}
