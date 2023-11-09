// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.InlineCompletionOvertyper.UpdatedElements
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElementManipulator
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Responsible for updating currently rendered [InlineCompletionElement] when a user types a new fragment.
 *
 * Note that your [InlineCompletionOvertyper] will be called only for elements your provider generated.
 * You may rely on the fact, that you will not receive [InlineCompletionElement] of other providers.
 *
 * Cycle of updating currently rendered elements:
 * * [overtype] is called for the current context and 'new elements' are returned.
 * * **All current elements are disposed**.
 * * If `new_elements` are `not null`, they are rendered.
 * * If `new_elements` are `null`, the provided event is used to start a new session.
 *
 * @see TypingEvent
 * @see InlineCompletionProvider.overtyper
 * @see InlineCompletionElementManipulator
 */
interface InlineCompletionOvertyper {

  /**
   * Updates [context] with respect to new typing [typing]. If [context] can be updated,
   * then [UpdatedElements] with new elements is returned and new elements will be rendered right away.
   * Otherwise, `null` is returned, as the result the current session will be invalidated
   * and [typing] will be considered as an event to start a new session.
   *
   * If there is no elements after over typing, this method should return `null`.
   *
   * Note that before rendering new elements, all current elements from [context] are disposed.
   */
  @RequiresEdt
  @RequiresBlockingContext
  fun overtype(context: InlineCompletionContext, typing: TypingEvent): UpdatedElements?

  /**
   * @param elements represent elements that will be rendered after over typing.
   * @param overtypedLength represents a number of symbols that were over typed during this update.
   * This number is for logs only, it does not influence on the execution.
   */
  class UpdatedElements(val elements: List<InlineCompletionElement>, val overtypedLength: Int)


  abstract class Adapter : InlineCompletionOvertyper {
    final override fun overtype(context: InlineCompletionContext, typing: TypingEvent): UpdatedElements? {
      return when (typing) {
        is TypingEvent.OneSymbol -> onOneSymbol(context, typing)
        is TypingEvent.NewLine -> onNewLine(context, typing)
        is TypingEvent.PairedEnclosureInsertion -> onPairedEnclosureInsertion(context, typing)
      }
    }

    protected open fun onOneSymbol(context: InlineCompletionContext, typing: TypingEvent.OneSymbol): UpdatedElements? {
      return null
    }

    protected open fun onNewLine(context: InlineCompletionContext, typing: TypingEvent.NewLine): UpdatedElements? {
      return null
    }

    protected open fun onPairedEnclosureInsertion(
      context: InlineCompletionContext,
      typing: TypingEvent.PairedEnclosureInsertion
    ): UpdatedElements? {
      return null
    }
  }
}


/**
 * Default variant of [InlineCompletionOvertyper] that takes into account only [TypingEvent.OneSymbol].
 * Other typings cause clearing currently displayed elements and a new session is started.
 *
 * If a new typed symbol matches the first rendered symbol in the current [InlineCompletionContext],
 * then the first non-empty element is truncated by one character using [InlineCompletionElementManipulator.truncateFirstSymbol].
 * Subsequent elements are rendered again.
 *
 * If no [InlineCompletionElementManipulator] for an element is found, then an update is considered as failed,
 * and a new session is started.
 *
 * Note: all empty elements ([InlineCompletionElement.text] is empty) at the start are truncated as well.
 *
 * @see InlineCompletionElementManipulator
 */
open class DefaultInlineCompletionOvertyper : InlineCompletionOvertyper.Adapter() {
  final override fun onOneSymbol(context: InlineCompletionContext, typing: TypingEvent.OneSymbol): UpdatedElements? {
    val fragment = typing.typed
    check(fragment.length == 1)
    if (!context.textToInsert().startsWith(fragment) || context.textToInsert() == fragment) {
      return null
    }
    return truncateFirstSymbol(context.state.elements.map { it.element })?.let { UpdatedElements(it, 1) }
  }

  private fun truncateFirstSymbol(elements: List<InlineCompletionElement>): List<InlineCompletionElement>? {
    val newFirstElementIndex = elements.indexOfFirst { it.text.isNotEmpty() }
    check(newFirstElementIndex >= 0)
    val firstElement = elements[newFirstElementIndex]
    val manipulator = InlineCompletionElementManipulator.getApplicable(firstElement) ?: return null
    val newFirstElement = manipulator.truncateFirstSymbol(firstElement)
    return listOfNotNull(newFirstElement) + elements.drop(newFirstElementIndex + 1)
  }
}
