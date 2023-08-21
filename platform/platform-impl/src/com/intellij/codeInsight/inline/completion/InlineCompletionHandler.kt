// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.getInlineCompletionContextOrNull
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.initOrGetInlineCompletionContext
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.initOrGetInlineCompletionContextWithPlaceholder
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.resetInlineCompletionContextWithPlaceholder
import com.intellij.codeInsight.inline.completion.InlineState.Companion.getInlineCompletionState
import com.intellij.codeInsight.inline.completion.InlineState.Companion.initOrGetInlineCompletionState
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.onCompletion
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Experimental
class InlineCompletionHandler(private val scope: CoroutineScope) : CodeInsightActionHandler {
  private var runningJob: Job? = null

  private fun getProvider(event: InlineCompletionEvent): InlineCompletionProvider? {
    return InlineCompletionProvider.extensions().firstOrNull { it.isEnabled(event) }
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val inlineState = editor.getInlineCompletionState() ?: return

    showInlineSuggestion(editor, inlineState, editor.caretModel.offset)
  }

  fun invoke(event: DocumentEvent, editor: Editor) = invoke(InlineCompletionEvent.DocumentChange(event, editor))
  fun invoke(event: EditorMouseEvent) = invoke(InlineCompletionEvent.CaretMove(event))
  fun invoke(event: LookupEvent) = invoke(InlineCompletionEvent.LookupChange(event))
  fun invoke(editor: Editor, file: PsiFile, caret: Caret) = invoke(InlineCompletionEvent.DirectCall(editor, file, caret))

  private fun shouldShowPlaceholder(): Boolean = Registry.`is`("inline.completion.show.placeholder")

  private fun invoke(event: InlineCompletionEvent) {
    if (isMuted.get()) {
      return
    }
    // TODO: move to launch
    val request = event.toRequest() ?: return
    val provider = getProvider(event) ?: return

    runningJob?.cancel()
    runningJob = scope.launch {
      val modificationStamp = request.document.modificationStamp
      val resultFlow = withContext(Dispatchers.IO) {
        provider.getProposals(request)
      }

      val editor = request.editor
      val offset = request.endOffset

      val inlineState = editor.initOrGetInlineCompletionState()

      withContext(Dispatchers.EDT) {
        showPlaceholder(editor, offset)

        resultFlow.onCompletion { if (it != null) disposePlaceholder(editor) }.collectIndexed { index, value ->
          disposePlaceholder(editor)
          if (index == 0 && modificationStamp != request.document.modificationStamp) {
            cancel()
            return@collectIndexed
          }

          if (index == 0) {
            inlineState.suggestions = listOf(InlineCompletionElement(value.text))
            showInlineSuggestion(editor, inlineState, offset)
          }
          else {
            if (editor.getInlineCompletionContextOrNull() == null) {
              cancel()
            }
            ensureActive()
            inlineState.suggestions = inlineState.suggestions.map { it.withText(it.text + value.text) }
            showInlineSuggestion(editor, inlineState, offset)
          }
        }
      }
    }
  }

  private fun showPlaceholder(editor: Editor, startOffset: Int, placeholder: String = "...") {
    //disposePlaceholder(editor)
    if (!shouldShowPlaceholder()) return

    val ctx = editor.initOrGetInlineCompletionContextWithPlaceholder()
    ctx.update(listOf(InlineCompletionElement(placeholder)), 0, startOffset)
  }

  private fun disposePlaceholder(editor: Editor) {
    if (!shouldShowPlaceholder()) return

    editor.resetInlineCompletionContextWithPlaceholder()
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
    val KEY = Key.create<InlineCompletionHandler>("inline.completion.handler")

    val isMuted: AtomicBoolean = AtomicBoolean(false)
    fun mute(): Unit = isMuted.set(true)
    fun unmute(): Unit = isMuted.set(false)
  }
}
