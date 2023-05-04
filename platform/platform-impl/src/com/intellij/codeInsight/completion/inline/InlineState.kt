package com.intellij.codeInsight.completion.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InlineState private constructor(
  var suggestionIndex: Int = 0,
  var lastStartOffset: Int = 0,
  var lastModificationStamp: Long = 0,
  var suggestions: List<InlineCompletionProposal> = emptyList(),
) {
  fun resetMeta() {
    lastModificationStamp = 0
  }

  fun init() {
    suggestionIndex = 0
  }

  companion object {
    private val INLINE_COMPLETION_STATE: Key<InlineState> = Key.create("inline.completion.state")

    fun Editor.getInlineState(): InlineState? = getUserData(INLINE_COMPLETION_STATE)
    fun Editor.initOrGetInlineState(): InlineState = getInlineState() ?: InlineState().also { putUserData(INLINE_COMPLETION_STATE, it) }
    fun Editor.resetInlineState() = putUserData(INLINE_COMPLETION_STATE, null)
  }
}
