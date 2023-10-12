// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.InlineCompletionPrefixTruncator.UpdatedElements
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Responsible for updating currently rendered [InlineCompletionElement] when a user types a new fragment.
 *
 * Note that your [InlineCompletionPrefixTruncator] will be called only for elements your provider generated.
 * You may rely on the fact, that you will not receive [InlineCompletionElement] of other providers.
 *
 * Cycle of updating currently rendered elements:
 * * [truncate] is called for the current context and 'new elements' are returned.
 * * **All current elements are disposed**.
 * * If `new_elements` are `not null`, they are rendered.
 * * If `new_elements` are `null`, the provided event is used to start a new session.
 *
 * So, make sure that your custom [InlineCompletionElement] are ready to be re-used after disposing,
 * or make sure that you copy them before re-rendering. See [DefaultInlineCompletionPrefixTruncator.copyBlock].
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
   * Note that before rendering new elements, all current elements from [context] are disposed.
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


  abstract class Adapter : InlineCompletionPrefixTruncator {
    final override fun truncate(context: InlineCompletionContext, typing: TypingEvent): UpdatedElements? {
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
 * Default variant of [InlineCompletionPrefixTruncator] that takes into account only [TypingEvent.OneSymbol].
 * Other typings cause clearing currently displayed elements and a new session is started.
 *
 * If a new typed symbol matches the first rendered symbol in the current [InlineCompletionContext],
 * then the first non-empty element is truncated by one character using [truncateFirstSymbol].
 * Subsequent elements are copied using [copyBlock] and rendered.
 *
 * If your custom elements are not supposed to be re-used after disposing, override [copyBlock] and return new instances.
 * The default implementation of [copyBlock] just returns the same [InlineCompletionElement], which works properly with the default
 * implementations of [InlineCompletionElement].
 *
 * Note: all empty elements ([InlineCompletionElement.text] is empty) at the start are truncated as well.
 */
open class DefaultInlineCompletionPrefixTruncator : InlineCompletionPrefixTruncator.Adapter() {
  final override fun onOneSymbol(context: InlineCompletionContext, typing: TypingEvent.OneSymbol): UpdatedElements? {
    val fragment = typing.typed
    check(fragment.length == 1)
    if (!context.textToInsert().startsWith(fragment) || context.textToInsert() == fragment) {
      return null
    }
    return truncateFirstSymbol(context.state.elements.map { it.element })?.let { UpdatedElements(it, 1) }
  }

  /**
   * Returns an instance of [InlineCompletionElement] that has the same content as [block].
   * It is used to re-render old non-changed elements when updating currently rendered elements.
   *
   * It might be useful to override this method, if your [InlineCompletionElement] doesn't allow re-usage after disposing.
   * All default implementations of [InlineCompletionElement] support re-usage, so this method returns [block] by default.
   *
   * @see InlineCompletionPrefixTruncator
   */
  protected open fun copyBlock(block: InlineCompletionElement): InlineCompletionElement {
    return block
  }

  /**
   * Returns a new state after truncating [block] by the first symbol.
   *
   * The default implementation may truncate only the provided implementations of [InlineCompletionElement].
   * Custom implementations of [InlineCompletionElement] make this method return [OneSymbolTruncationResult.Fail],
   * which causes all elements to be cleared and a new session to start.
   * So, if your provider may produce other [InlineCompletionElement], override this method to properly handle typed symbols.
   *
   * * [OneSymbolTruncationResult.Remainder] denotes that after truncation [block] by one symbol, there are still other symbols
   * in [block], and this block may still be rendered.
   * * [OneSymbolTruncationResult.Emptied] denotes that after truncation [block] by one symbol, there are no other symbols
   * in [block], and this block mayn't be rendered anymore. Still this truncation is considered as successful.
   * * [OneSymbolTruncationResult.Fail] denotes that truncation failed which causes all elements to be cleared and a new session to start.
   */
  protected open fun truncateFirstSymbol(block: InlineCompletionElement): OneSymbolTruncationResult {
    return if (block !is InlineCompletionGrayTextElement) {
      OneSymbolTruncationResult.Fail
    }
    else if (block.text.length > 1) {
      OneSymbolTruncationResult.Remainder(InlineCompletionGrayTextElement(block.text.drop(1)))
    }
    else {
      OneSymbolTruncationResult.Emptied
    }
  }

  private fun truncateFirstSymbol(elements: List<InlineCompletionElement>): List<InlineCompletionElement>? {
    val newFirstElementIndex = elements.indexOfFirst { it.text.isNotEmpty() }
    check(newFirstElementIndex >= 0)
    val newFirstElement = when (val truncated = truncateFirstSymbol(elements[newFirstElementIndex])) {
      is OneSymbolTruncationResult.Remainder -> truncated.element
      OneSymbolTruncationResult.Emptied -> null
      OneSymbolTruncationResult.Fail -> return null
    }
    return listOfNotNull(newFirstElement) + elements.drop(newFirstElementIndex + 1).map { copyBlock(it) }
  }

  /**
   * @see truncateFirstSymbol
   */
  protected sealed interface OneSymbolTruncationResult {
    class Remainder(val element: InlineCompletionElement) : OneSymbolTruncationResult

    data object Emptied : OneSymbolTruncationResult

    data object Fail : OneSymbolTruncationResult
  }
}
