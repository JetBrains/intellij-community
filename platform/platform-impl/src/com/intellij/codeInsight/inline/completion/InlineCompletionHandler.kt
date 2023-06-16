// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.initOrGetInlineCompletionContext
import com.intellij.codeInsight.inline.completion.InlineState.Companion.getInlineCompletionState
import com.intellij.codeInsight.inline.completion.InlineState.Companion.initOrGetInlineCompletionState
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Experimental
class InlineCompletionHandler(private val scope: CoroutineScope) : CodeInsightActionHandler {
  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val inlineState = editor.getInlineCompletionState() ?: return

    showInlineSuggestion(editor, inlineState, editor.caretModel.offset)
  }

  fun invoke(event: DocumentEvent, editor: Editor, provider: InlineCompletionProvider) {
    val request = InlineCompletionRequest.fromDocumentEvent(event, editor) ?: return
    invoke(request, provider)
  }

  fun invoke(request: InlineCompletionRequest, provider: InlineCompletionProvider) {
    scope.launch {
      val modificationStamp = request.document.modificationStamp
      val result = provider.getProposals(request)

      val editor = request.editor
      val offset = request.endOffset

      val inlineState = editor.initOrGetInlineCompletionState()
      if (modificationStamp == request.document.modificationStamp) {
        inlineState.suggestions = result
      }

      withContext(Dispatchers.EDT) {
        showInlineSuggestion(editor, inlineState, offset)
      }
    }
  }

  private fun showInlineSuggestion(editor: Editor, inlineContext: InlineState, startOffset: Int) {
    val suggestions = inlineContext.suggestions
    if (suggestions.isEmpty()) {
      return
    }

    val idOffset = 1 // TODO: replace with 0?
    val size = suggestions.size

    val suggestionIndex = (inlineContext.suggestionIndex + idOffset + size) % size
    if (suggestions.getOrNull(suggestionIndex) == null) {
      return
    }

    editor.initOrGetInlineCompletionContext().update(suggestions, suggestionIndex, startOffset)

    inlineContext.suggestionIndex = suggestionIndex
    inlineContext.lastStartOffset = startOffset
    inlineContext.lastModificationStamp = editor.document.modificationStamp
  }

  companion object {
    val isMuted: AtomicBoolean = AtomicBoolean(false)
    fun mute(): Unit = isMuted.set(true)
    fun unmute(): Unit = isMuted.set(false)
  }
}
