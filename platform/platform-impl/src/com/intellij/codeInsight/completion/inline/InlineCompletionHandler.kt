package com.intellij.codeInsight.completion.inline

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.completion.inline.InlineContext.Companion.initOrGetInlineContext
import com.intellij.codeInsight.completion.inline.InlineState.Companion.getInlineState
import com.intellij.codeInsight.completion.inline.InlineState.Companion.initOrGetInlineState
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
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

@ApiStatus.Internal
class InlineCompletionHandler constructor(private val scope: CoroutineScope) : CodeInsightActionHandler {
  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val inlineState = editor.getInlineState() ?: return

    showInlineSuggestion(editor, inlineState, editor.caretModel.offset)
  }

  fun invoke(event: DocumentEvent, editor: Editor, provider: InlineCompletionProvider,) {
    val request = InlineCompletionRequest.fromDocumentEvent(event, editor) ?: return
    invoke(request, provider)
  }

  fun invoke(request: InlineCompletionRequest, provider: InlineCompletionProvider) {
    scope.launch {
      val result = provider.getProposals(request)

      val editor = request.editor
      val offset = request.endOffset

      val inlineState = editor.initOrGetInlineState()

      inlineState.suggestions = result
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

    editor.initOrGetInlineContext().update(suggestions, suggestionIndex, startOffset)
  }

  companion object {
    private val LOG = thisLogger()

    val isMuted = AtomicBoolean(false)
    fun mute() = isMuted.set(true)
    fun unmute() = isMuted.set(false)
  }
}
