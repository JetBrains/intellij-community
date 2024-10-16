// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.highlighting.BraceMatcher
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.codeInsight.inline.completion.elements.*
import com.intellij.codeInsight.inline.completion.utils.InlineCompletionSkipElementUtils.insertOffsetsAndAdditionalLines
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actions.EditorActionUtil
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Experimental
private class InlineCompletionPartialAcceptHandlerImpl : InlineCompletionPartialAcceptHandler {

  override fun insertNextWord(
    editor: Editor,
    file: PsiFile,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    val completion = elements.joinToString("") { it.text }.takeIf { it.isNotEmpty() } ?: return elements
    val offset = editor.caretModel.offset
    val textWithCompletion = editor.document.text.substring(0, offset) + completion
    return withFakeEditor(textWithCompletion, offset, file.fileType, editor.project) { editorWithCompletion ->
      executeInsertNextWord(editor, editorWithCompletion, file, offset, completion, elements)
    }
  }

  override fun insertNextLine(
    editor: Editor,
    file: PsiFile,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    val completion = elements.joinToString("") { it.text }.takeIf { it.isNotEmpty() } ?: return elements
    val offset = editor.caretModel.offset
    val textWithCompletion = editor.document.text.substring(0, offset) + completion
    return withFakeEditor(textWithCompletion, offset, file.fileType, editor.project) { editorWithCompletion ->
      executeInsertNextLine(editor, editorWithCompletion, file, offset, completion, elements)
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
    var prefixLength = maxOf(findOffsetDeltaForInsertNextWord(editorWithCompletion, offset), 1)
    val fileType = originalFile.fileType
    val iterator = editorWithCompletion.highlighter.createIterator(offset)
    val braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator)
    val quoteHandlerEx = InlineCompletionQuoteHandlerEx.getAdapter(originalFile, originalEditor)
    val skipOffsetsAfterInsertion = mutableListOf<Int>()
    val stuckDetector = IteratorStuckDetector(iterator)
    iteratorLabel@ while (!iterator.atEnd() && iterator.start < offset + prefixLength) {
      if (!stuckDetector.iterateIfStuck()) {
        break
      }
      val (startOffset, _, tokenText) = iterator.getTokenInfoRelativelyTo(completion, offset) ?: continue
      val foundQuotePair = iterator.checkForOpenQuoteAndReturnPair(
        tokenOffset = startOffset,
        initialOffset = offset,
        textWithCompletion = textWithCompletion,
        quoteHandlerEx = quoteHandlerEx
      )
      if (foundQuotePair != null) {
        val closingRange = foundQuotePair.closingQuoteRange
        if (closingRange != null) {
          for (i in closingRange.startOffset until closingRange.endOffset) {
            skipOffsetsAfterInsertion += i - offset
          }
          // We should insert the whole token if it consists of more than one symbol
          // E.g., we need to insert all the three quotes in """.
          // E.g., we DO NOT need to insert the entire '1' token.
          if (foundQuotePair.openingQuoteRange.length > 1) {
            prefixLength = maxOf(prefixLength, startOffset - offset + foundQuotePair.openingQuoteRange.length)
          }
        }
        if (!iterator.atEnd()) {
          iterator.advance()
        }
        continue
      }
      val foundBracesPair = iterator.checkForOpenBraceAndReturnPair(
        tokenText = tokenText,
        textWithCompletion = textWithCompletion,
        fileType = fileType,
        braceMatcher = braceMatcher
      )
      if (foundBracesPair != null) {
        if (foundBracesPair.closingBracketRange != null) {
          for (i in foundBracesPair.closingBracketRange) {
            skipOffsetsAfterInsertion += i - offset
          }
          // We should insert the whole token
          prefixLength = maxOf(prefixLength, startOffset - offset + foundBracesPair.bracket.length)
        }
        if (!iterator.atEnd()) {
          iterator.advance()
        }
        continue
      }
      if (!iterator.atEnd()) {
        iterator.advance()
      }
    }
    return doInsert(originalEditor, originalFile.project, offset, completion, prefixLength, skipOffsetsAfterInsertion, elements)
  }

  private fun executeInsertNextLine(
    originalEditor: Editor,
    editorWithCompletion: Editor,
    originalFile: PsiFile,
    offset: Int,
    completion: String,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    val textWithCompletion = editorWithCompletion.document.text
    val prefixLength = findOffsetDeltaForInsertNextLine(completion)

    val skipOffsetsAfterInsertion = mutableListOf<Int>()
    var iterator = editorWithCompletion.highlighter.createIterator(offset)
    val fileType = originalFile.fileType
    val braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator)
    val quoteHandlerEx = InlineCompletionQuoteHandlerEx.getAdapter(originalFile, originalEditor)
    val stuckDetector = IteratorStuckDetector(iterator)
    while (!iterator.atEnd() && iterator.start < offset + prefixLength) {
      if (!stuckDetector.iterateIfStuck()) {
        break
      }
      val (startOffset, _, tokenText) = iterator.getTokenInfoRelativelyTo(completion, offset) ?: continue
      val foundQuotePair = iterator.checkForOpenQuoteAndReturnPair(
        tokenOffset = startOffset,
        initialOffset = offset,
        textWithCompletion = textWithCompletion,
        quoteHandlerEx = quoteHandlerEx
      )
      if (foundQuotePair != null) {
        val closingRange = foundQuotePair.closingQuoteRange
        if (closingRange == null) {
          break // The literal covers the left of the completion
        }
        if (closingRange.startOffset >= offset + prefixLength) {
          for (i in closingRange.startOffset until closingRange.endOffset) {
            skipOffsetsAfterInsertion += i - offset
          }
          break // no other braces and literals are expected
        }
        // Iterator is placed right before the closing quote
        if (!iterator.atEnd()) {
          iterator.advance()
        }
        continue
      }
      val foundBracesPair = iterator.checkForOpenBraceAndReturnPair(
        tokenText = tokenText,
        textWithCompletion = textWithCompletion,
        fileType = fileType,
        braceMatcher = braceMatcher
      )
      if (foundBracesPair != null) {
        val closingBracketRange = foundBracesPair.closingBracketRange
        if (closingBracketRange != null) {
          if (closingBracketRange.first >= offset + prefixLength) {
            for (i in closingBracketRange) {
              skipOffsetsAfterInsertion += i - offset
            }
            iterator = editorWithCompletion.highlighter.createIterator(startOffset)
          }
          // Otherwise, it's optimization: we start from the closing brace in the inserted prefix
          // We rely on that the completion respects brackets balance
        }
        else {
          iterator = editorWithCompletion.highlighter.createIterator(startOffset)
        }
      }

      if (!iterator.atEnd()) {
        iterator.advance()
      }
    }
    return doInsert(originalEditor, originalFile.project, offset, completion, prefixLength, skipOffsetsAfterInsertion, elements)
  }

  private fun findOffsetDeltaForInsertNextLine(completion: String): Int {
    val insertLeadingWhitespaces = Registry.`is`("inline.completion.insert.line.with.leading.whitespaces")
    var prefixLength = 0
    for (sym in completion) {
      if (sym == '\n') {
        if (insertLeadingWhitespaces) {
          prefixLength += completion.countWhilePredicate(start = prefixLength) { it.isWhitespace() }
          break
        }
        if (prefixLength > 0) { // Otherwise, completion starts from the '\n' and we need to insert the next line
          break
        }
      }
      prefixLength++
    }
    return prefixLength
  }

  private fun findOffsetDeltaForInsertNextWord(editorWithCompletion: Editor, initialOffset: Int): Int {
    val initialCaretOffset = editorWithCompletion.caretModel.offset
    editorWithCompletion.caretModel.moveToOffset(initialOffset)
    EditorActionUtil.moveCaretToNextWord(editorWithCompletion, false, editorWithCompletion.getSettings().isCamelWords())
    val finalOffset = editorWithCompletion.caretModel.offset
    editorWithCompletion.caretModel.moveToOffset(initialCaretOffset)
    return finalOffset - initialOffset
  }

  private fun HighlighterIterator.checkForOpenBraceAndReturnPair(
    tokenText: String,
    textWithCompletion: String,
    fileType: FileType,
    braceMatcher: BraceMatcher
  ): FoundBracesPair? {
    if (!braceMatcher.isLBraceToken(this, textWithCompletion, fileType)) {
      return null
    }
    val isMatched = BraceMatchingUtil.matchBrace(textWithCompletion, fileType, this, true)
    return FoundBracesPair(
      tokenText,
      if (isMatched) start until end else null
    )
  }

  private fun HighlighterIterator.getTokenInfoRelativelyTo(
    completion: String,
    completionOffset: Int
  ): RelativeTokenInfo? {
    if (tokenType == null) {
      return null
    }
    val text = completion.substring(maxOf(start - completionOffset, 0), end - completionOffset)
    return RelativeTokenInfo(start, end, text).takeIf { text.isNotEmpty() }
  }

  private fun HighlighterIterator.checkForOpenQuoteAndReturnPair(
    tokenOffset: Int,
    initialOffset: Int,
    textWithCompletion: String,
    quoteHandlerEx: InlineCompletionQuoteHandlerEx.Adapter?
  ): FoundQuotesPair? {
    if (quoteHandlerEx == null || initialOffset > tokenOffset) return null
    val quoteOpeningRange = quoteHandlerEx.getOpeningQuoteRange(textWithCompletion, this, tokenOffset) ?: return null
    val quoteClosingRange = quoteHandlerEx.findClosingQuoteRange(textWithCompletion, this, tokenOffset)
    return FoundQuotesPair(quoteOpeningRange, quoteClosingRange)
  }

  private fun doInsert(
    originalEditor: Editor,
    project: Project,
    offset: Int,
    completion: String,
    prefixLength: Int,
    skipOffsets: List<Int>,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    val elementsAfterPrefixInsertion = doInsertPrefix(originalEditor, offset, completion.take(prefixLength), elements)
    val result = elementsAfterPrefixInsertion.insertOffsetsAndAdditionalLines(
      skipOffsets.map { it - prefixLength },
      originalEditor,
      originalEditor.caretModel.offset,
      completion.drop(prefixLength)
    )
    PsiDocumentManager.getInstance(project).commitDocument(originalEditor.document)
    return result
  }

  private fun doInsertPrefix(
    originalEditor: Editor,
    offset: Int,
    prefix: String,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    val finalElements = LinkedList(elements)
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
    return finalElements.toMutableList()
  }

  private fun String.countWhilePredicate(start: Int = 0, end: Int = length, predicate: (Char) -> Boolean): Int {
    for (i in start until end) {
      if (!predicate(this[i])) {
        return i - start
      }
    }
    return end - start
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

  private class IteratorStuckDetector(private val iterator: HighlighterIterator) {

    private var lastStartOffset: Int? = null

    fun iterateIfStuck(): Boolean {
      if (iterator.start == lastStartOffset) {
        iterator.advance()
      }
      if (iterator.atEnd()) {
        return false
      }
      lastStartOffset = iterator.start
      return true
    }
  }

  private data class FoundQuotesPair(val openingQuoteRange: TextRange, val closingQuoteRange: TextRange?)

  private data class FoundBracesPair(val bracket: String, val closingBracketRange: IntRange?)

  /**
   * @param startOffset corresponds to the start offset of the token **in the whole document**
   * @param endOffset corresponds to the end offset of the token **in the whole document**
   * @param text corresponds to the text of the token trimmed by the start of the completion
   */
  private data class RelativeTokenInfo(val startOffset: Int, val endOffset: Int, val text: String)

  companion object {
    private val LOG = logger<InlineCompletionPartialAcceptHandler>()
  }
}
