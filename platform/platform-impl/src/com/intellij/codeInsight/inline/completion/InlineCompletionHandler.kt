// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.getInlineCompletionContextOrNull
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.initOrGetInlineCompletionContext
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.initOrGetInlineCompletionContextWithPlaceholder
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.resetInlineCompletionContextWithPlaceholder
import com.intellij.codeInsight.inline.completion.InlineState.Companion.getInlineCompletionState
import com.intellij.codeInsight.inline.completion.InlineState.Companion.initOrGetInlineCompletionState
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEmpty
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Experimental
class InlineCompletionHandler(private val scope: CoroutineScope) : CodeInsightActionHandler {
  private var runningJob: Job? = null
  private var lastInvocationTime = 0L

  private fun getProvider(event: InlineCompletionEvent): InlineCompletionProvider? {
    return InlineCompletionProvider.extensions().firstOrNull { it.isEnabled(event) }?.also {
      LOG.trace("Selected inline provider: $it")
    }
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val inlineState = editor.getInlineCompletionState() ?: return

    // TODO another implementation? Currently there is no inline triggering
    //showInlineSuggestion(editor, inlineState, editor.caretModel.offset, triggerTracker)
  }

  fun invoke(event: InlineCompletionEvent.DocumentChange) = invokeEvent(event)
  fun invoke(event: InlineCompletionEvent.CaretMove) = invokeEvent(event)
  fun invoke(event: InlineCompletionEvent.LookupChange) = invokeEvent(event)
  fun invoke(event: InlineCompletionEvent.DirectCall) = invokeEvent(event)

  @Deprecated(
    "replaced with direct event call type",
    ReplaceWith("invoke(InlineCompletionEvent.DocumentChange(event, editor))"),
    DeprecationLevel.ERROR
  )
  fun invoke(event: DocumentEvent, editor: Editor) {
    return invoke(InlineCompletionEvent.DocumentChange(event, editor))
  }

  @Deprecated(
    "replaced with direct event call type",
    ReplaceWith("invoke(InlineCompletionEvent.CaretMove(event))"),
    DeprecationLevel.ERROR
  )
  fun invoke(event: EditorMouseEvent) {
    return invoke(InlineCompletionEvent.CaretMove(event))
  }

  @Deprecated(
    "replaced with direct event call type",
    ReplaceWith("invoke(InlineCompletionEvent.LookupChange(event))"),
    DeprecationLevel.ERROR
  )
  fun invoke(event: LookupEvent) {
    return invoke(InlineCompletionEvent.LookupChange(event))
  }

  @Deprecated(
    "replaced with direct event call type",
    ReplaceWith("invoke(InlineCompletionEvent.DirectCall(editor, file, caret, context))"),
    DeprecationLevel.ERROR
  )
  fun invoke(editor: Editor, file: PsiFile, caret: Caret, context: DataContext?) {
    return invoke(InlineCompletionEvent.DirectCall(editor, file, caret, context))
  }

  private fun shouldShowPlaceholder(): Boolean = Registry.`is`("inline.completion.show.placeholder")

  private fun invokeEvent(event: InlineCompletionEvent) {
    LOG.trace("Start processing inline event $event")
    if (isMuted.get()) {
      LOG.trace("Muted")
      return
    }

    val request = event.toRequest() ?: return

    LOG.trace("Schedule new job")
    runningJob?.cancel()
    runningJob = scope.launch { invokeDebounced(event, request) }
  }

  private suspend fun invokeDebounced(event: InlineCompletionEvent, request: InlineCompletionRequest) {
    lastInvocationTime = System.currentTimeMillis()
    val provider = getProvider(event) ?: return
    val triggerTracker = InlineCompletionUsageTracker.TriggerTracker(lastInvocationTime, event, provider)
    val modificationStamp = request.document.modificationStamp
    val resultFlow = withContext(Dispatchers.IO) { provider.getProposals(request) }
    val placeholder = provider.getPlaceholder(request)

    val editor = request.editor
    val offset = request.endOffset

    val inlineState = editor.initOrGetInlineCompletionState()

    // If you write a test and observe an infinite hang here, set [UsefulTestCase.runInDispatchThread] to false.
    withContext(Dispatchers.EDT) {
      showPlaceholder(editor, offset, placeholder, triggerTracker)

      resultFlow
        .onEmpty {
          disposePlaceholder(editor)
          triggerTracker.noSuggestions()
        }
        .onCompletion {
          if (it != null) {
            disposePlaceholder(editor)
            if (it is CancellationException || it is ProcessCanceledException) {
              triggerTracker.cancelled()
            }
            else {
              triggerTracker.exception()
            }
          }
          triggerTracker.finished(editor.project)
        }
        .collectIndexed { index, value ->
          if (index == 0) {
            disposePlaceholder(editor)
            triggerTracker.hasSuggestion()
          }
          if (index == 0 && modificationStamp != request.document.modificationStamp) {
            cancel()
            return@collectIndexed
          }

          if (index == 0) {
            inlineState.suggestions = listOf(InlineCompletionElement(value.text))
            showInlineSuggestion(editor, inlineState, offset, triggerTracker)
          }
          else {
            if (editor.getInlineCompletionContextOrNull() == null) {
              cancel()
            }
            ensureActive()
            inlineState.suggestions = inlineState.suggestions.map { it.withText(it.text + value.text) }
            showInlineSuggestion(editor, inlineState, offset, triggerTracker)
          }
        }
    }
  }

  private fun showPlaceholder(
    editor: Editor,
    startOffset: Int,
    placeholder: InlineCompletionPlaceholder,
    triggerTracker: InlineCompletionUsageTracker.TriggerTracker) {
    LOG.trace("Trying to show placeholder")
    if (!shouldShowPlaceholder()) return

    val ctx = editor.initOrGetInlineCompletionContextWithPlaceholder(triggerTracker)
    ctx.update(listOf(placeholder.element), 0, startOffset)
  }

  private fun disposePlaceholder(editor: Editor) {
    LOG.trace("Trying to dispose placeholder")
    if (!shouldShowPlaceholder()) return

    editor.resetInlineCompletionContextWithPlaceholder()
  }

  private fun showInlineSuggestion(editor: Editor,
                                   inlineContext: InlineState,
                                   startOffset: Int,
                                   triggerTracker: InlineCompletionUsageTracker.TriggerTracker) {
    val suggestions = inlineContext.suggestions
    LOG.trace("Trying to show inline suggestions $suggestions")
    if (suggestions.isEmpty()) {
      return
    }

    val idOffset = 1 // TODO: replace with 0?
    val size = suggestions.size

    val suggestionIndex = (inlineContext.suggestionIndex + idOffset + size) % size
    if (suggestions.getOrNull(suggestionIndex) == null) {
      return
    }

    editor.initOrGetInlineCompletionContext(triggerTracker).update(suggestions, suggestionIndex, startOffset)

    inlineContext.suggestionIndex = suggestionIndex
    inlineContext.lastStartOffset = startOffset
    inlineContext.lastModificationStamp = editor.document.modificationStamp
  }

  companion object {
    private val LOG = thisLogger()
    val KEY = Key.create<InlineCompletionHandler>("inline.completion.handler")

    val isMuted: AtomicBoolean = AtomicBoolean(false)
    fun mute(): Unit = isMuted.set(true)
    fun unmute(): Unit = isMuted.set(false)
  }
}
