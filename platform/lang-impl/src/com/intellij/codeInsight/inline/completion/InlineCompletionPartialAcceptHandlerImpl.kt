// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.editorActions.QuoteHandler
import com.intellij.codeInsight.editorActions.TypedHandler
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.codeInsight.inline.completion.InlineCompletionPartialAcceptHandlerImpl.SearchState.*
import com.intellij.codeInsight.inline.completion.elements.*
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionPartialAcceptHandler
import com.intellij.codeInsight.inline.completion.utils.insertSkipElementsAt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import kotlin.time.measureTimedValue

@ApiStatus.Experimental
internal class InlineCompletionPartialAcceptHandlerImpl : InlineCompletionPartialAcceptHandler {

  override fun insertNextWord(
    editor: Editor,
    file: PsiFile,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    val completion = elements.joinToString("") { it.text }
    val offset = editor.caretModel.offset
    val textWithCompletion = editor.document.text.substring(0, offset) + completion
    return withFakeEditor(textWithCompletion, offset, file.fileType, editor.project) { editorWithCompletion ->
      executeInsertNextWord(editor, editorWithCompletion, file, offset, completion, elements)
    }
  }

  private fun executeInsertNextWord(
    originalEditor: Editor,
    editorWithCompletion: Editor,
    originalFile: PsiFile,
    offset: Int,
    completion: String,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    val textWithCompletion = editorWithCompletion.document.text
    val fileType = originalFile.fileType
    val iterator = editorWithCompletion.highlighter.createIterator(offset)
    val braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator)
    val quoteHandler = TypedHandler.getQuoteHandler(originalFile, originalEditor)
    var insertedPrefixLength = 0
    var searchState = INIT
    val skipOffsetsAfterInsertion = mutableListOf<Int>()
    iteratorLabel@ while (!iterator.atEnd()) {
      iterator.tokenType ?: break
      val startOffset = iterator.start
      val endOffset = iterator.end
      val tokenText = completion.substring(maxOf(0, startOffset - offset), endOffset - offset)
      if (tokenText.isEmpty()) {
        continue
      }
      if (offset <= startOffset && quoteHandler != null && quoteHandler.isOpeningQuote(iterator, startOffset)) {
        val quote = tokenText.takeWhile { it == tokenText[0] }
        insertedPrefixLength += quote.length
        val quoteEndOffset = iterator.findClosingQuoteOffset(quoteHandler)
        if (quoteEndOffset != null) {
          val closingQuote = textWithCompletion.substring(
            quoteEndOffset,
            minOf(quoteEndOffset + quote.length, textWithCompletion.length)
          )
          if (closingQuote == quote) {
            repeat(quote.length) {
              skipOffsetsAfterInsertion += quoteEndOffset + it - offset
            }
          }
        }
        break
      }
      if (braceMatcher.isLBraceToken(iterator, textWithCompletion, fileType)) {
        insertedPrefixLength += tokenText.length
        val isMatched = BraceMatchingUtil.matchBrace(textWithCompletion, fileType, iterator, true)
        if (isMatched) {
          for (i in iterator.start until iterator.end) {
            skipOffsetsAfterInsertion += i - offset
          }
        }
        break
      }
      if (braceMatcher.isRBraceToken(iterator, textWithCompletion, fileType)) {
        insertedPrefixLength += tokenText.length
        break
      }
      for ((index, sym) in tokenText.withIndex()) {
        if (quoteHandler != null && quoteHandler.isClosingQuote(iterator, maxOf(startOffset, offset) + index)) {
          insertedPrefixLength += tokenText.length - index
          break@iteratorLabel
        }
        searchState = searchState.updateWith(sym) ?: break@iteratorLabel
        insertedPrefixLength++
      }
      iterator.advance()
    }
    insertedPrefixLength = maxOf(insertedPrefixLength, 1)
    return doInsert(originalEditor, offset, completion, insertedPrefixLength, skipOffsetsAfterInsertion, elements)
  }

  private fun HighlighterIterator.findClosingQuoteOffset(quoteHandler: QuoteHandler): Int? {
    var balance = 0
    while (!atEnd()) {
      val startOffset = start
      val endOffset = end
      for (i in startOffset until endOffset) {
        if (quoteHandler.isOpeningQuote(this, i)) {
          balance++
        }
        else if (quoteHandler.isClosingQuote(this, i)) {
          balance--
          if (balance < 0) {
            return null
          }
          if (balance == 0) {
            return i
          }
        }
      }
      advance()
    }
    return null
  }

  private fun doInsert(
    originalEditor: Editor,
    offset: Int,
    completion: String,
    prefixLength: Int,
    skipOffsets: List<Int>,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    val elementsAfterPrefixInsertion = doInsertPrefix(originalEditor, offset, completion.take(prefixLength), elements)
    return doInsertSuffix(
      originalEditor,
      originalEditor.caretModel.offset,
      completion,
      prefixLength,
      skipOffsets,
      elementsAfterPrefixInsertion
    )
  }

  private fun doInsertPrefix(
    originalEditor: Editor,
    offset: Int,
    prefix: String,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    val finalElements = elements.toMutableList()
    var prefixDone = 0
    while (prefixDone < prefix.length) {
      val prefixLeft = prefix.length - prefixDone
      var element = finalElements.firstOrNull() ?: break
      if (element.text.isEmpty()) {
        finalElements.removeFirst()
        continue
      }
      when (element) {
        is InlineCompletionSkipTextElement -> {
          if (prefixLeft >= element.text.length) {
            prefixDone += element.text.length
            finalElements.removeFirst()
          }
          else {
            prefixDone = prefix.length
            finalElements[0] = InlineCompletionSkipTextElement(element.text.substring(prefixLeft))
          }
        }
        else -> {
          val toTruncate = minOf(prefixLeft, element.text.length)
          originalEditor.document.insertString(offset + prefixDone, element.text.substring(0, toTruncate))
          var manipulator = InlineCompletionElementManipulator.getApplicable(element)
          if (manipulator == null) {
            // Fallback to a regular completion element
            element = InlineCompletionGrayTextElement(element.text)
            manipulator = InlineCompletionGrayTextElementManipulator()
            LOG.error("No inline completion manipulator was found for ${element::class.qualifiedName}.")
          }
          val firstElement = manipulator.substring(element, toTruncate, element.text.length)
          if (firstElement == null) {
            finalElements.removeFirst()
          }
          else {
            finalElements[0] = firstElement
          }
          prefixDone += toTruncate
        }
      }
    }
    originalEditor.caretModel.moveToOffset(offset + prefix.length)
    return finalElements
  }

  private fun doInsertSuffix(
    originalEditor: Editor,
    finalOffset: Int,
    completion: String,
    prefixLength: Int,
    skipOffsets: List<Int>,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    val suffix = skipOffsets.joinToString("") { completion[it].toString() }
    originalEditor.document.insertString(finalOffset, suffix)
    return elements.insertSkipElementsAt(skipOffsets.map { it - prefixLength })
  }

  private inline fun <T> withFakeEditor(
    text: String,
    offset: Int,
    fileType: FileType,
    project: Project?,
    block: (editorWithCompletion: Editor) -> T
  ): T {
    val editorFactory = EditorFactory.getInstance()
    val fakeDocument = editorFactory.createDocument(text)
    val fakeEditor = editorFactory.createEditor(fakeDocument, project, fileType, true)
    return try {
      fakeEditor.caretModel.moveToOffset(offset)
      block(fakeEditor)
    }
    finally {
      editorFactory.releaseEditor(fakeEditor)
    }
  }

  private fun SearchState.updateWith(symbol: Char): SearchState? {
    return if (symbol.isLetterOrDigit()) {
      when (this) {
        INIT -> LETTER_OR_DIGIT
        LETTER_OR_DIGIT -> LETTER_OR_DIGIT
        SYMBOLS -> LETTER_OR_DIGIT_AFTER_SYMBOLS
        LETTER_OR_DIGIT_AFTER_SYMBOLS -> LETTER_OR_DIGIT_AFTER_SYMBOLS
        SYMBOLS_AFTER_LETTER_OR_DIGIT -> null
      }
    }
    else {
      when (this) {
        INIT -> SYMBOLS
        LETTER_OR_DIGIT -> SYMBOLS_AFTER_LETTER_OR_DIGIT
        SYMBOLS -> SYMBOLS
        LETTER_OR_DIGIT_AFTER_SYMBOLS -> null
        SYMBOLS_AFTER_LETTER_OR_DIGIT -> SYMBOLS_AFTER_LETTER_OR_DIGIT
      }
    }
  }

  private enum class SearchState {
    INIT,
    LETTER_OR_DIGIT,
    SYMBOLS,
    LETTER_OR_DIGIT_AFTER_SYMBOLS,
    SYMBOLS_AFTER_LETTER_OR_DIGIT
  }

  companion object {
    private val LOG = logger<InlineCompletionPartialAcceptHandler>()
  }
}
