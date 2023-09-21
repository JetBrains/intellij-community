// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.InlineCompletionRequestManager.InvalidateRequest
import com.intellij.codeInsight.inline.completion.InlineCompletionRequestManager.ProposedRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.util.concurrency.annotations.RequiresEdt


internal data class SimpleTypingEvent(val typed: String, val caretMoves: Boolean)

internal interface InlineCompletionRequestManager {

  @RequiresEdt
  fun getRequest(event: InlineCompletionEvent): ProposedRequest

  @RequiresEdt
  fun allowDocumentChange(event: SimpleTypingEvent)

  data class ProposedRequest(val request: InlineCompletionRequest?, val invalidate: InvalidateRequest)

  sealed interface InvalidateRequest {
    data object Ignore : InvalidateRequest

    data class Invalidate(val editor: Editor) : InvalidateRequest
  }

  companion object {
    operator fun invoke(): InlineCompletionRequestManager = InlineCompletionRequestManagerImpl()
  }
}

private class InlineCompletionRequestManagerImpl : InlineCompletionRequestManager {

  private var lastSimpleEvent: SimpleTypingEvent? = null

  override fun getRequest(event: InlineCompletionEvent): ProposedRequest {
    return when (event) {
      is InlineCompletionEvent.DocumentChange -> onDocumentChange(event)
      else -> ProposedRequest(event.toRequest(), InvalidateRequest.Ignore)
    }
  }

  override fun allowDocumentChange(event: SimpleTypingEvent) {
    lastSimpleEvent = event
  }

  private fun onDocumentChange(event: InlineCompletionEvent.DocumentChange): ProposedRequest {
    val documentEvent = event.event
    val lastSimpleEvent = lastSimpleEvent
    this.lastSimpleEvent = null
    return if (lastSimpleEvent != null && lastSimpleEvent.matches(documentEvent)) {
      val request = event.toRequest()?.let { initialRequest ->
        if (lastSimpleEvent.caretMoves) initialRequest else initialRequest.shiftOffset(-lastSimpleEvent.typed.length)
      }
      ProposedRequest(request, InvalidateRequest.Ignore)
    }
    else {
      val request = if (documentEvent.isBlankSequenceInserted()) event.toRequest() else null
      ProposedRequest(request, InvalidateRequest.Invalidate(event.editor))
    }
  }

  private fun SimpleTypingEvent.matches(event: DocumentEvent): Boolean {
    return typed == event.newFragment.toString()
  }

  private fun DocumentEvent.isBlankSequenceInserted(): Boolean {
    return oldLength == 0 && newLength > 0 && newFragment.isBlank()
  }

  private fun InlineCompletionRequest.shiftOffset(delta: Int): InlineCompletionRequest {
    return copy(startOffset = startOffset + delta, endOffset = endOffset + delta)
  }
}
