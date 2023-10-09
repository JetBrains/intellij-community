// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.InlineCompletionPrefixTruncator.UpdatedElements
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Responsible for updating currently rendered [InlineCompletionElement] when a user types a new fragment.
 *
 * Note, that your [InlineCompletionPrefixTruncator] will be called only for elements your provider generated.
 * You may rely on the fact, that you will not receive [InlineCompletionElement] of other providers.
 *
 * @see TypingEvent
 * @see InlineCompletionProvider.prefixTruncator
 */
interface InlineCompletionPrefixTruncator {

  /**
   * Updates [context] with respect to new typing [typing]. If [context] can be updated,
   * then [UpdatedElements] with new elements is returned and new elements will be rendered right away.
   * Otherwise, `null` is returned, as the result the current session will be invalidated
   * and [typing] will be considered as an event to start a new session.
   *
   * If there is no elements after truncation, this method should return `null`.
   *
   * **Note**. Do not return any provided elements. Before returning, copy them using [InlineCompletionElement.withSameContent].
   */
  @RequiresEdt
  @RequiresBlockingContext
  fun truncate(context: InlineCompletionContext, typing: TypingEvent): UpdatedElements?

  /**
   * @param elements represent elements that will be rendered after truncating.
   * @param truncatedLength represents a number of symbols that were truncated during this update.
   * This number is for logs only, it does not influence on the execution.
   */
  data class UpdatedElements(val elements: List<InlineCompletionElement>, val truncatedLength: Int)
}


/**
 * Standard variant of [InlineCompletionPrefixTruncator] that takes into account only [TypingEvent.Simple].
 *
 * If a new typed symbol matches the first rendered symbol in the current [InlineCompletionContext],
 * then the first non-empty element is truncated by one character using [InlineCompletionElement.withTruncatedPrefix].
 * Subsequent elements are copied using [InlineCompletionElement.withSameContent].
 *
 * Note: all empty elements ([InlineCompletionElement.text] is empty) at the start are truncated as well.
 */
open class StandardInlineCompletionPrefixTruncator : InlineCompletionPrefixTruncator {
  override fun truncate(context: InlineCompletionContext, typing: TypingEvent): UpdatedElements? {
    if (typing !is TypingEvent.Simple) {
      return null
    }
    val fragment = typing.typed
    check(fragment.length == 1)
    if (!context.textToInsert().startsWith(fragment) || context.textToInsert() == fragment) {
      return null
    }
    val newElements = truncateFirstSymbol(context.state.elements.map { it.element })
    return UpdatedElements(newElements, 1)
  }

  /**
   * This is a safe implementation that truncates the first symbol.
   * It takes into account that some elements at the start can have an empty text (as the result, they are truncated).
   */
  private fun truncateFirstSymbol(elements: List<InlineCompletionElement>): List<InlineCompletionElement> {
    val newFirstElementIndex = elements.indexOfFirst { it.text.isNotEmpty() }
    check(newFirstElementIndex >= 0)
    val newFirstElement = elements[newFirstElementIndex].withTruncatedPrefix(1)
    return listOfNotNull(newFirstElement) + elements.drop(newFirstElementIndex + 1).map { it.withSameContent() }
  }
}
