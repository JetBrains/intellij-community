// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.util.concurrency.annotations.RequiresEdt


internal data class SimpleTypingEvent(val typed: String, val caretMoves: Boolean)

internal class InlineCompletionRequestManager(@RequiresEdt private val invalidate: () -> Unit) {

  private var lastSimpleEvent: SimpleTypingEvent? = null
  private var lastRequest: InlineCompletionRequest? = null

  @RequiresEdt
  fun getRequest(event: InlineCompletionEvent): InlineCompletionRequest? {
    lastRequest = when (event) {
      is InlineCompletionEvent.DocumentChange -> onDocumentChange(event)
      is InlineCompletionEvent.InlineNavigationEvent -> lastRequest
      else -> event.toRequest()
    }
    return lastRequest
  }

  @RequiresEdt
  fun allowDocumentChange(event: SimpleTypingEvent) {
    lastSimpleEvent = event
  }

  private fun onDocumentChange(event: InlineCompletionEvent.DocumentChange): InlineCompletionRequest? {
    val documentEvent = event.event
    val lastSimpleEvent = lastSimpleEvent
    this.lastSimpleEvent = null
    return if (lastSimpleEvent != null && lastSimpleEvent.matches(documentEvent)) {
      event.toRequest()?.let { initialRequest ->
        if (lastSimpleEvent.caretMoves) initialRequest else initialRequest.shiftOffset(-lastSimpleEvent.typed.length)
      }
    }
    else {
      invalidate()
      if (documentEvent.isBlankSequenceInserted()) event.toRequest() else null
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
