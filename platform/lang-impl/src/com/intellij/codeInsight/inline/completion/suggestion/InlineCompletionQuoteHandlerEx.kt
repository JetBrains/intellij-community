// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.editorActions.QuoteHandler
import com.intellij.codeInsight.editorActions.TypedHandler
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/**
 * **This functionality is intended to be used only within handling Inline Completion.**
 *
 * The reason why [QuoteHandler] is not enough. Languages have different incompatible behaviour between each other.
 * E.g., wrong behaviors:
 * * In Python, `<caret>"string"` is not considered as an open quote.
 * * In JS, the start of a string literal in backquotes is considered as an open quote.
 *
 * This interface tries to unify the behavior required for Inline Completion. We need to understand when
 * we actually have an opening or closing quote.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface InlineCompletionQuoteHandlerEx {

  /**
   * Returns the [TextRange] of the opening quote if:
   * * We are exactly of its start: `<caret>"string"`
   * * We are in the middle of it: `"<caret>""string"""`
   *
   * The returned range mustn't start before [offset].
   *
   * @param quoteHandler original [QuoteHandler] for a language/file type.
   */
  fun getOpeningQuoteRange(
    documentText: String,
    iterator: HighlighterIterator,
    offset: Int,
    quoteHandler: QuoteHandler
  ): TextRange? {
    return if (quoteHandler.isOpeningQuote(iterator, offset)) {
      var endOffset = offset + 1
      // Doesn't work with with empty Python triple quotes ("""""")
      val iteratorEnd = iterator.end
      while (endOffset < iteratorEnd && documentText[endOffset] == documentText[offset]) {
        endOffset++
      }
      TextRange(offset, endOffset)
    }
    else null
  }

  /**
   * Returns a closing quote if we are at the **exactly** its start.
   *
   * @param quoteHandler original [QuoteHandler] for a language/file type.
   */
  fun getClosingQuoteRange(
    documentText: String,
    iterator: HighlighterIterator,
    offset: Int,
    quoteHandler: QuoteHandler
  ): TextRange? {
    return if (quoteHandler.isClosingQuote(iterator, offset)) TextRange(offset, iterator.end) else null
  }

  /**
   * Serves to fastly find a closing quote when [getOpeningQuoteRange] returns non-null value for [offset].
   *
   * If it returns a null, then a brute-force search starts to work. See [Adapter.findClosingQuoteRange].
   */
  fun getClosingQuoteRangeForOpening(
    documentText: String,
    iterator: HighlighterIterator,
    offset: Int,
    quoteHandler: QuoteHandler
  ): TextRange? {
    return null
  }

  private class Default : InlineCompletionQuoteHandlerEx

  @ApiStatus.Internal
  class Adapter(private val quoteHandler: QuoteHandler, private val quoteHandlerEx: InlineCompletionQuoteHandlerEx) {
    fun getOpeningQuoteRange(documentText: String, iterator: HighlighterIterator, offset: Int): TextRange? {
      return quoteHandlerEx.getOpeningQuoteRange(documentText, iterator, offset, quoteHandler)
    }

    fun getClosingQuoteRange(documentText: String, iterator: HighlighterIterator, offset: Int): TextRange? {
      return quoteHandlerEx.getClosingQuoteRange(documentText, iterator, offset, quoteHandler)
    }

    fun getClosingQuoteRangeForOpening(documentText: String, iterator: HighlighterIterator, offset: Int): TextRange? {
      return quoteHandlerEx.getClosingQuoteRangeForOpening(documentText, iterator, offset, quoteHandler)
    }

    /**
     * Finds a closing quote that corresponds to the opening one at [offset].
     * It is guaranteed that [getOpeningQuoteRange] returned non-null value.
     *
     * At first it tries to use [getClosingQuoteRangeForOpening]. If it fails, it counts balance of the following
     * start and end quotes.
     */
    fun findClosingQuoteRange(documentText: String, iterator: HighlighterIterator, offset: Int): TextRange? {
      getClosingQuoteRangeForOpening(documentText, iterator, offset)?.let {
        return it
      }
      var balance = 0
      while (!iterator.atEnd()) {
        val endOffset = iterator.end
        var i = iterator.start
        while (i < endOffset) {
          val openingQuoteRange = getOpeningQuoteRange(documentText, iterator, i)
          if (openingQuoteRange != null) {
            balance++
            i = openingQuoteRange.endOffset
            continue
          }
          val closingQuoteRange = getClosingQuoteRange(documentText, iterator, i)
          if (closingQuoteRange != null) {
            balance--
            if (balance == 0) {
              return closingQuoteRange
            }
            i = closingQuoteRange.endOffset
            continue
          }
          i++
        }
        iterator.advance()
      }
      return null
    }
  }

  companion object {
    private val EP = LanguageExtension<InlineCompletionQuoteHandlerEx>("com.intellij.inline.completion.quoteHandlerEx")

    internal fun getAdapter(file: PsiFile, editor: Editor): Adapter? {
      val quoteHandler = TypedHandler.getQuoteHandler(file, editor) ?: return null
      val quoteHandlerEx = EP.forLanguage(file.viewProvider.baseLanguage) ?: Default()
      return Adapter(quoteHandler, quoteHandlerEx)
    }
  }
}
