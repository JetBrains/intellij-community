// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElementManipulator
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager.UpdateResult.*
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * This interface defines methods that are responsible for updating inline completion suggestions based on various events.
 * Implementors of this interface handle different types of events and accordingly produce updated suggestions.
 *
 * Some rules:
 * * If the currently displayed variant was emptied (it contains no elements after update), then the current session would be cleared.
 * * If the variant, that's not currently displayed, was emptied, then the variant would be invalidated but session would not.
 */
interface InlineCompletionSuggestionUpdateManager {

  /**
   * Updates the inline completion suggestions based on a given event and variant.
   *
   * It is called on some event when inline completion variants are already provided and initialized (they may be not computed yet).
   */
  @RequiresEdt
  @RequiresBlockingContext
  fun update(event: InlineCompletionEvent, variant: InlineCompletionVariant.Snapshot): UpdateResult

  /**
   * Updates the inline completion when a provider has not returned a list of variants yet, but the session is already initialized.
   *
   * **It is experimental** and by default the session is not invalidated until [InlineCompletionEvent.DocumentChange] comes.
   *
   * Since there are no variants available, the method returns either `true`, or `false`.
   *
   * @return `true` if the current session should stay the same.
   * `false` if the session should be invalidated and a new one should be started.
   */
  @ApiStatus.Experimental
  @RequiresEdt
  @RequiresBlockingContext
  fun updateWhileNoVariants(event: InlineCompletionEvent): Boolean {
    return event !is InlineCompletionEvent.DocumentChange
  }

  /**
   * A sealed interface that represents the result of an update to the inline completion variants.
   *
   * There are three possible states represented by specific instances:
   *
   * 1. [Changed] - Indicates that the update resulted in a change of the inline completion variant. Carries the new snapshot.
   * 2. [Same] - Indicates that the update made no changes; the inline completion variant remained the same.
   * 3. [Invalidated] - Indicates that the variant is invalidated due to the update.
   * It means that this variant will no longer be available to choose.
   */
  sealed interface UpdateResult {
    class Changed(val snapshot: InlineCompletionVariant.Snapshot) : UpdateResult

    data object Same : UpdateResult

    data object Invalidated : UpdateResult
  }

  interface Adapter : InlineCompletionSuggestionUpdateManager {

    override fun update(event: InlineCompletionEvent, variant: InlineCompletionVariant.Snapshot): UpdateResult {
      return when (event) {
        is InlineCompletionEvent.DocumentChange -> onDocumentChange(event, variant)
        is InlineCompletionEvent.DirectCall -> onDirectCall(event, variant)
        is InlineCompletionEvent.InlineLookupEvent -> onLookupEvent(event, variant)
        else -> onCustomEvent(event, variant)
      }
    }

    @RequiresEdt
    @RequiresBlockingContext
    fun onDocumentChange(event: InlineCompletionEvent.DocumentChange, variant: InlineCompletionVariant.Snapshot): UpdateResult = Invalidated

    @RequiresEdt
    @RequiresBlockingContext
    fun onDirectCall(event: InlineCompletionEvent.DirectCall, variant: InlineCompletionVariant.Snapshot): UpdateResult = Same

    @RequiresEdt
    @RequiresBlockingContext
    fun onLookupEvent(event: InlineCompletionEvent.InlineLookupEvent, variant: InlineCompletionVariant.Snapshot): UpdateResult = Same

    @RequiresEdt
    @RequiresBlockingContext
    fun onCustomEvent(event: InlineCompletionEvent, variant: InlineCompletionVariant.Snapshot): UpdateResult = Same
  }

  /**
   * A default implementation of the [InlineCompletionSuggestionUpdateManager] interface.
   * This implementation specializes in handling [InlineCompletionEvent.DocumentChange] events
   * with a typing event of type [TypingEvent.OneSymbol].
   *
   * It validates the typing and truncates the first symbol for the inline completion variants.
   * Other typings invalidate variants.
   *
   * To support truncation of your own [InlineCompletionElement], see [InlineCompletionElementManipulator].
   */
  open class Default : Adapter {
    override fun onDocumentChange(event: InlineCompletionEvent.DocumentChange, variant: InlineCompletionVariant.Snapshot): UpdateResult {
      if (!isValidTyping(event.typing, variant)) {
        return Invalidated
      }
      val truncated = truncateFirstSymbol(variant.elements) ?: return Invalidated
      return Changed(variant.copy(elements = truncated))
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
