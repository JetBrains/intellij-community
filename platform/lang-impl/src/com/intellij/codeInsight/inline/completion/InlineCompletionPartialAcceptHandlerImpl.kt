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

  private fun executeInsertNextLine(
    originalEditor: Editor,
    editorWithCompletion: Editor,
    originalFile: PsiFile,
    offset: Int,
    completion: String,
    elements: List<InlineCompletionElement>
  ): List<InlineCompletionElement> {
    var newLineAppeared = false
    var insertionLength = 0
    for (element in elements) {
      if (element.text.contains('\n')) {
        newLineAppeared = true
        val newLineOffset = element.text.indexOf('\n')
        val prefixLength = newLineOffset + element.text.countWhilePredicate(start = newLineOffset) { it.isWhitespace() }
        insertionLength += prefixLength
        if (prefixLength < element.text.length) {
          break
        }
      }
      else if (!newLineAppeared) {
        insertionLength += element.text.length
      }
      else {
        val prefixLength = element.text.countWhilePredicate(start = 0) { it.isWhitespace() }
        insertionLength += prefixLength
        if (prefixLength < element.text.length) {
          break
        }
      }
    }
    return doInsert(originalEditor, offset, completion, insertionLength, emptyList(), elements)
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
    val newSkipOffsets = insertSkipSymbolsWithAdditionalLineBreaks(
      originalEditor,
      finalOffset,
      completion.drop(prefixLength),
      prefixLength,
      skipOffsets,
      elements
    )
    return elements.insertSkipElementsAt(newSkipOffsets.map { it - prefixLength })
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
   * @return the initial skip offsets with new skip offsets responsible to newly inserted symbols.
   */
  private fun insertSkipSymbolsWithAdditionalLineBreaks(
    originalEditor: Editor,
    finalOffset: Int,
    trimmedCompletion: String,
    prefixLength: Int,
    skipOffsets: List<Int>,
    elements: List<InlineCompletionElement>
  ): List<Int> {
    val initialSkipOffsets = skipOffsets
      .map { it - prefixLength }
      .filter { it < trimmedCompletion.length && it >= 0 }
      .distinct()
      .sorted()
    if (initialSkipOffsets.isEmpty()) {
      return emptyList()
    }

    val lineNumberToOffsets = List(trimmedCompletion.count { it == '\n' } + 1) { mutableListOf<Int>() }
    var elementIndex = 0
    var lineNumber = 0
    var currentOffset = 0
    for (skipOffset in initialSkipOffsets) {
      while (elementIndex < elements.size && currentOffset + elements[elementIndex].text.length < skipOffset) {
        currentOffset += elements[elementIndex].text.length
        lineNumber += elements[elementIndex].text.count { it == '\n' }
        elementIndex++
      }
      check(elementIndex < elements.size)
      val element = elements[elementIndex]
      val relativeSkipOffset = skipOffset - currentOffset
      lineNumber += element.text.take(relativeSkipOffset).count { it == '\n' }
      lineNumberToOffsets[lineNumber] += skipOffset
    }

    val lineToExists = MutableList(lineNumberToOffsets.size) { false }
    lineToExists[0] = true
    lineNumber = 0
    for (element in elements) {
      val newLinesNumber = element.text.count { it == '\n' }
      if (element is InlineCompletionSkipTextElement) {
        for (i in lineNumber + 1..lineNumber + newLinesNumber) {
          lineToExists[i] = true
        }
      }
      lineNumber += newLinesNumber
    }

    val lineBreaksOffsets = trimmedCompletion.indices.filter { trimmedCompletion[it] == '\n' }

    data class LineFix(val lineNumber: Int, val startSkip: Int, val endSkip: Int)

    val lineFixes = mutableListOf<LineFix>()
    for (lineNum in lineNumberToOffsets.indices.filter { lineNumberToOffsets[it].isNotEmpty() }) {
      if (lineToExists[lineNum]) {
        val lineStart = lineBreaksOffsets.getOrNull(lineNum - 1)?.plus(1) ?: 0
        lineFixes += LineFix(lineNum, lineStart, lineStart) // To insert skip symbols on the already existent line
        continue
      }
      check(lineNumber > 0)
      val breakStart = lineBreaksOffsets[lineNum - 1]
      val rangeEnd = breakStart + trimmedCompletion.countWhilePredicate(start = breakStart) { it.isWhitespace() }
      lineFixes += LineFix(lineNum, breakStart, rangeEnd)
    }

    var insertionOffset = finalOffset
    val labeledSkipOffsets = labelSkipOffsets(elements)
    currentOffset = 0
    for ((lineNum, newSkipOffsetStart, newSkipOffsetEnd) in lineFixes) {
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
          originalEditor.document.insertString(insertionOffset, trimmedCompletion[i].toString())
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
          originalEditor.document.insertString(insertionOffset, trimmedCompletion[currentSkipOffset].toString())
          insertionOffset++
        }
      }
    }

    return (initialSkipOffsets + lineFixes.flatMap { it.startSkip until it.endSkip })
      .distinct()
      .map { it + prefixLength }
  }

  private fun labelSkipOffsets(elements: List<InlineCompletionElement>): List<Boolean> {
    val labels = MutableList(elements.sumOf { it.text.length }) { false }
    var offset = 0
    for (element in elements) {
      repeat(element.text.length) {
        labels[offset] = element is InlineCompletionSkipTextElement
        offset++
      }
    }
    return labels
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
