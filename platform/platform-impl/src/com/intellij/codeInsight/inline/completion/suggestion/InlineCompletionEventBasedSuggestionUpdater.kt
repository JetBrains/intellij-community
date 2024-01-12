// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElementManipulator
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

// TODO docs
@ApiStatus.Experimental
interface InlineCompletionEventBasedSuggestionUpdater {

  @RequiresEdt
  @RequiresBlockingContext
  fun update(event: InlineCompletionEvent, variant: InlineCompletionVariant.Snapshot): UpdateResult

  @ApiStatus.Experimental
  fun updateWhileNoVariants(event: InlineCompletionEvent): Boolean

  sealed interface UpdateResult {
    class Changed(val snapshot: InlineCompletionVariant.Snapshot) : UpdateResult

    data object Same : UpdateResult

    data object Invalidated : UpdateResult
  }

  interface Adapter : InlineCompletionEventBasedSuggestionUpdater {

    override fun update(event: InlineCompletionEvent, variant: InlineCompletionVariant.Snapshot): UpdateResult {
      return when (event) {
        is InlineCompletionEvent.DocumentChange -> onDocumentChange(event, variant)
        is InlineCompletionEvent.DirectCall -> onDirectCall(event, variant)
        is InlineCompletionEvent.InlineLookupEvent -> onLookupEvent(event, variant)
        else -> onCustomEvent(event, variant)
      }
    }

    override fun updateWhileNoVariants(event: InlineCompletionEvent): Boolean {
      return event !is InlineCompletionEvent.DocumentChange
    }

    @RequiresEdt
    @RequiresBlockingContext
    fun onDocumentChange(event: InlineCompletionEvent.DocumentChange, variant: InlineCompletionVariant.Snapshot): UpdateResult {
      return UpdateResult.Invalidated
    }

    @RequiresEdt
    @RequiresBlockingContext
    fun onDirectCall(event: InlineCompletionEvent.DirectCall, variant: InlineCompletionVariant.Snapshot): UpdateResult {
      return UpdateResult.Same
    }

    @RequiresEdt
    @RequiresBlockingContext
    fun onLookupEvent(event: InlineCompletionEvent.InlineLookupEvent, variant: InlineCompletionVariant.Snapshot): UpdateResult {
      return UpdateResult.Same
    }

    @RequiresEdt
    @RequiresBlockingContext
    fun onCustomEvent(event: InlineCompletionEvent, variant: InlineCompletionVariant.Snapshot): UpdateResult.Same {
      return UpdateResult.Same
    }
  }

  open class Default : Adapter {
    override fun onDocumentChange(event: InlineCompletionEvent.DocumentChange, variant: InlineCompletionVariant.Snapshot): UpdateResult {
      if (!isValidTyping(event.typing, variant)) {
        return UpdateResult.Invalidated
      }
      val truncated = truncateFirstSymbol(variant.elements) ?: return UpdateResult.Invalidated
      return UpdateResult.Changed(variant.copy(elements = truncated))
    }

    private fun isValidTyping(typing: TypingEvent, variant: InlineCompletionVariant.Snapshot): Boolean {
      if (typing !is TypingEvent.OneSymbol) {
        return false
      }
      val fragment = typing.typed
      val textToInsert = variant.elements.joinToString("") { it.text }
      return textToInsert.startsWith(fragment)
    }

    private fun truncateFirstSymbol(elements: List<InlineCompletionElement>): List<InlineCompletionElement>? {
      val newFirstElementIndex = elements.indexOfFirst { it.text.isNotEmpty() }
      check(newFirstElementIndex >= 0)
      val firstElement = elements[newFirstElementIndex]
      val manipulator = InlineCompletionElementManipulator.getApplicable(firstElement) ?: return null
      val newFirstElement = manipulator.truncateFirstSymbol(firstElement)
      return listOfNotNull(newFirstElement) + elements.drop(newFirstElementIndex + 1)
    }

    companion object {
      internal val INSTANCE = Default()
    }
  }
}
