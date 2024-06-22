// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.highlighting.BraceMatcher
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.codeInsight.inline.completion.elements.*
import com.intellij.codeInsight.inline.completion.utils.insertSkipElementsAt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import java.util.LinkedList
import kotlin.collections.plusAssign
import kotlin.collections.sorted

@ApiStatus.Experimental
private class InlineCompletionPartialAcceptHandlerImpl : InlineCompletionPartialAcceptHandler {

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

  override fun insertNextLine(
    editor: Editor,
    file: PsiFile,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    val completion = elements.joinToString("") { it.text }
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
    val fileType = originalFile.fileType
    val iterator = editorWithCompletion.highlighter.createIterator(offset)
    val braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator)
    val quoteHandlerEx = InlineCompletionQuoteHandlerEx.getAdapter(originalFile, originalEditor)
    var insertedPrefixLength = 0
    var searchState = SearchState.INIT
    val skipOffsetsAfterInsertion = mutableListOf<Int>()
    val stuckDetector = IteratorStuckDetector(iterator)
    iteratorLabel@ while (!iterator.atEnd()) {
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
        insertedPrefixLength += foundQuotePair.openingQuoteRange.length
        val closingRange = foundQuotePair.closingQuoteRange
        if (closingRange != null) {
          for (i in closingRange.startOffset until closingRange.endOffset) {
            skipOffsetsAfterInsertion += i - offset
          }
        }
        break
      }
      val foundBracesPair = iterator.checkForOpenBraceAndReturnPair(
        tokenText = tokenText,
        textWithCompletion = textWithCompletion,
        fileType = fileType,
        braceMatcher = braceMatcher
      )
      if (foundBracesPair != null) {
        insertedPrefixLength += foundBracesPair.bracket.length
        if (foundBracesPair.closingBracketRange != null) {
          for (i in foundBracesPair.closingBracketRange) {
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
        if (quoteHandlerEx != null) {
          val closingRange = quoteHandlerEx.getClosingQuoteRange(textWithCompletion, iterator, maxOf(startOffset, offset) + index)
          if (closingRange != null) {
            insertedPrefixLength += closingRange.length
            break@iteratorLabel
          }
        }
        searchState = searchState.updateWith(sym) ?: break@iteratorLabel
        insertedPrefixLength++
      }
      iterator.advance()
    }
    insertedPrefixLength = maxOf(insertedPrefixLength, 1)
    return doInsert(originalEditor, originalFile.project, offset, completion, insertedPrefixLength, skipOffsetsAfterInsertion, elements)
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
    var prefixLength = 0
    for (sym in completion) {
      prefixLength++
      if (sym == '\n') {
        prefixLength += completion.countWhilePredicate(start = prefixLength) { it.isWhitespace() }
        break
      }
    }
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
          if (closingBracketRange.start >= offset + prefixLength) {
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
    val result = doInsertSuffix(
      originalEditor,
      originalEditor.caretModel.offset,
      completion,
      prefixLength,
      skipOffsets,
      elementsAfterPrefixInsertion
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

  private fun doInsertSuffix(
    originalEditor: Editor,
    finalOffset: Int,
    completion: String,
    prefixLength: Int,
    skipOffsets: List<Int>,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    val newSkipOffsets = insertSkipOffsetsAndAdditionalLines(
      originalEditor,
      finalOffset,
      completion.drop(prefixLength),
      skipOffsets.map { it - prefixLength },
      elements
    )
    return elements.insertSkipElementsAt(newSkipOffsets)
  }

  /**
   * When we insert an open brace/quote, we'd like to insert the paired bracket/quote as well.
   * [skipOffsets] represent all the offsets in a completion, that need to be inserted in an editor.
   *
   * The problem: when the paired bracket/quote is located on another line, that doesn't exist in an editor yet,
   * we need to insert it as well.
   * Also, we need to insert all leading whitespaces before that bracket/quote (if they are not inserted yet),
   * to be sure that cancellation of the inline completion will leave the code with correct indentation.
   *
   * This method operates over forbidden magic, doing the following:
   * * It groups [skipOffsets] by their line.
   * * For each line we compute whether it's already existent checking [elements] on instance of [InlineCompletionSkipTextElement].
   * * For each non-existent line used in insertion, we compute leading whitespaces.
   * * If line or leading whitespaces do not exist, we add them to the editor and add their offsets to new skip elements.
   *
   * @return the initial skip offsets with new skip offsets responsible to newly inserted whitespaces.
   */
  private fun insertSkipOffsetsAndAdditionalLines(
    originalEditor: Editor,
    finalOffset: Int,
    trimmedCompletion: String,
    skipOffsets: List<Int>,
    elements: List<InlineCompletionElement>
  ): List<Int> {
    val initialSkipOffsets = skipOffsets
      .filter { it < trimmedCompletion.length && it >= 0 }
      .distinct()
      .sorted()
    if (initialSkipOffsets.isEmpty()) {
      return emptyList()
    }

    val numberOfLines = trimmedCompletion.count { it == '\n' } + 1
    val lineNumberToOffsets = mapOffsetsToLineNumber(numberOfLines, trimmedCompletion, initialSkipOffsets)
    val linesToInsert = computeLinesToInsertForSkipOffsets(lineNumberToOffsets, elements, trimmedCompletion)
    doInsertSkipOffsetsAndAdditionalLines(originalEditor, finalOffset, trimmedCompletion, elements, linesToInsert, lineNumberToOffsets)

    return (initialSkipOffsets + linesToInsert.flatMap { it.whitespaceStart until it.whitespaceEnd }).distinct()
  }

  private fun labelSkipOffsets(elements: List<InlineCompletionElement>): BooleanArray {
    val labels = BooleanArray(elements.sumOf { it.text.length }) { false }
    var offset = 0
    for (element in elements) {
      repeat(element.text.length) {
        labels[offset] = element is InlineCompletionSkipTextElement
        offset++
      }
    }
    return labels
  }

  /**
   * For each line in a completion, computes whether there is already an actual line in an editor.
   * Computation is based on checking instance of [InlineCompletionSkipTextElement]:
   * if such an element contains `\n`, then there is an actual line in an editor.
   *
   * The first line is always considered as existent because the caret is located on it.
   */
  private fun computeExistenceOfLines(numberOfLines: Int, elements: List<InlineCompletionElement>): List<Boolean> {
    val lineToExists = MutableList(numberOfLines) { false }
    lineToExists[0] = true
    var lineNumber = 0
    for (element in elements) {
      val newLinesNumber = element.text.count { it == '\n' }
      if (element is InlineCompletionSkipTextElement) {
        for (i in lineNumber + 1..lineNumber + newLinesNumber) {
          lineToExists[i] = true
        }
      }
      lineNumber += newLinesNumber
    }
    return lineToExists
  }

  /**
   * For each offset of [offsets], it computes the corresponding line number in [completion].
   *
   * @return mapping `line number -> all offsets in this line`.
   */
  private fun mapOffsetsToLineNumber(numberOfLines: Int, completion: String, offsets: List<Int>): List<List<Int>> {
    val lineNumberToOffsets = List(numberOfLines) { mutableListOf<Int>() }
    var lineNumber = 0
    var currentOffset = 0
    for (offset in offsets) {
      while (currentOffset < offset) {
        if (completion[currentOffset] == '\n') {
          lineNumber++
        }
        currentOffset++
      }
      lineNumberToOffsets[lineNumber] += offset
    }
    return lineNumberToOffsets
  }

  /**
   * This method computes all the descriptors for lines inside the [completion] that contain the initially skipped offsets.
   * Each descriptor contains the line number and what content should be inserted in the line.
   * This content is made of only whitespaces and defined by the offsets in the [completion].
   *
   * If returned offsets are equal, nothing is needed to be inserted in this line,
   * because it's already present in an editor.
   *
   * @param lineNumberToOffsets is a mapping from line number in the completion to all the
   *        skipped offsets that need to be inserted in the editor (see [mapOffsetsToLineNumber]).
   * @return all the [LineToInsert] that need to be processed to insert the initial skip offsets.
   * @see LineToInsert
   * @see insertSkipOffsetsAndAdditionalLines
   */
  private fun computeLinesToInsertForSkipOffsets(
    lineNumberToOffsets: List<List<Int>>,
    elements: List<InlineCompletionElement>,
    completion: String
  ): List<LineToInsert> {
    val newLinesDescriptors = mutableListOf<LineToInsert>()
    val lineToExists = computeExistenceOfLines(lineNumberToOffsets.size, elements)
    val lineBreaksOffsets = completion.indices.filter { completion[it] == '\n' }
    for (lineNum in lineNumberToOffsets.indices.filter { lineNumberToOffsets[it].isNotEmpty() }) {
      if (lineToExists[lineNum]) {
        val lineStart = lineBreaksOffsets.getOrNull(lineNum - 1)?.plus(1) ?: 0
        newLinesDescriptors += LineToInsert(lineNum, lineStart, lineStart) // To insert skip symbols on the already existent line
        continue
      }
      check(lineNum > 0)
      val breakStart = lineBreaksOffsets[lineNum - 1]
      val rangeEnd = breakStart + completion.countWhilePredicate(start = breakStart) { it.isWhitespace() }
      newLinesDescriptors += LineToInsert(lineNum, breakStart, rangeEnd)
    }
    return newLinesDescriptors
  }

  /**
   * The final stage of [insertSkipOffsetsAndAdditionalLines].
   *
   * Literally inserts symbols defined by skip offsets and new lines defined by [linesToInsert] at corresponding [startOffset].
   * The inserted skip offsets are located in [lineNumberToOffsets]: they are grouped by line number.
   *
   * @param linesToInsert the result of [computeLinesToInsertForSkipOffsets]
   * @param lineNumberToOffsets the result of [mapOffsetsToLineNumber]
   */
  private fun doInsertSkipOffsetsAndAdditionalLines(
    originalEditor: Editor,
    startOffset: Int,
    completion: String,
    elements: List<InlineCompletionElement>,
    linesToInsert: List<LineToInsert>,
    lineNumberToOffsets: List<List<Int>>
  ) {
    var currentOffset = 0
    var insertionOffset = startOffset
    val labeledSkipOffsets = labelSkipOffsets(elements)
    for ((lineNum, newSkipOffsetStart, newSkipOffsetEnd) in linesToInsert) {
      while (currentOffset < newSkipOffsetStart) {
        if (labeledSkipOffsets[currentOffset]) {
          insertionOffset++
        }
        currentOffset++
      }
      for (i in newSkipOffsetStart until newSkipOffsetEnd) {
        if (labeledSkipOffsets[i]) {
          insertionOffset++
        }
        else {
          originalEditor.document.insertString(insertionOffset, completion[i].toString())
          insertionOffset++
        }
      }
      var focusOffset = newSkipOffsetEnd
      for (currentSkipOffset in lineNumberToOffsets[lineNum].sorted()) {
        if (focusOffset > currentSkipOffset) {
          continue
        }
        while (focusOffset < currentSkipOffset) {
          if (labeledSkipOffsets[focusOffset]) {
            insertionOffset++
          }
          focusOffset++
        }
        if (!labeledSkipOffsets[currentSkipOffset]) {
          originalEditor.document.insertString(insertionOffset, completion[currentSkipOffset].toString())
          insertionOffset++
        }
      }
    }
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

  private fun SearchState.updateWith(symbol: Char): SearchState? {
    return if (symbol.isLetterOrDigit()) {
      when (this) {
        SearchState.INIT -> SearchState.LETTER_OR_DIGIT
        SearchState.LETTER_OR_DIGIT -> SearchState.LETTER_OR_DIGIT
        SearchState.SYMBOLS -> SearchState.LETTER_OR_DIGIT_AFTER_SYMBOLS
        SearchState.LETTER_OR_DIGIT_AFTER_SYMBOLS -> SearchState.LETTER_OR_DIGIT_AFTER_SYMBOLS
        SearchState.SYMBOLS_AFTER_LETTER_OR_DIGIT -> null
      }
    }
    else {
      when (this) {
        SearchState.INIT -> SearchState.SYMBOLS
        SearchState.LETTER_OR_DIGIT -> SearchState.SYMBOLS_AFTER_LETTER_OR_DIGIT
        SearchState.SYMBOLS -> SearchState.SYMBOLS
        SearchState.LETTER_OR_DIGIT_AFTER_SYMBOLS -> null
        SearchState.SYMBOLS_AFTER_LETTER_OR_DIGIT -> SearchState.SYMBOLS_AFTER_LETTER_OR_DIGIT
      }
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

  private enum class SearchState {
    INIT,
    LETTER_OR_DIGIT,
    SYMBOLS,
    LETTER_OR_DIGIT_AFTER_SYMBOLS,
    SYMBOLS_AFTER_LETTER_OR_DIGIT
  }

  /**
   * First, see [insertSkipOffsetsAndAdditionalLines].
   *
   * Represents a descriptor for a line that needs to be inserted in an editor with skip offsets.
   * When such a line is inserted, we insert `\n` and the leading whitespaces in this line.
   * So, it always looks like `\n + some spaces and tabs`.
   *
   * @param lineNumberInCompletion to which line **in the completion** it corresponds
   * @param whitespaceStart the offset in the completion from which the inserted whitespaces start.
   *        This offset always corresponds to the `\n` **before** this line.
   * @param whitespaceEnd the offset in the completion where the insertion part ends. It's end-*exclusive*.
   */
  private data class LineToInsert(val lineNumberInCompletion: Int, val whitespaceStart: Int, val whitespaceEnd: Int)

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
