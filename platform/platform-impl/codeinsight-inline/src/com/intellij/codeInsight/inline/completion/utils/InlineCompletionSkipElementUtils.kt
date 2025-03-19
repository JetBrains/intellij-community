// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.utils

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElementManipulator
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.codeInsight.inline.completion.utils.InlineCompletionSkipElementUtils.computeLinesToInsertForOffsets
import com.intellij.codeInsight.inline.completion.utils.InlineCompletionSkipElementUtils.insertOffsetsAndAdditionalLines
import com.intellij.codeInsight.inline.completion.utils.InlineCompletionSkipElementUtils.mapOffsetsToLineNumber
import com.intellij.openapi.editor.Editor
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
object InlineCompletionSkipElementUtils {

  /**
   * For each offset in [offsets]:
   * * Searches for the Inline Completion element and inner symbol corresponding to that offset
   * * Splits this element into two parts: strictly before that symbol and strictly after
   * * Removes the element from the resulting list
   *   and inserts the left split part, the new skip element with the symbol and the right split part
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  fun List<InlineCompletionElement>.insertSkipElementsAt(offsets: List<Int>): List<InlineCompletionElement> = buildList {
    val elements = this@insertSkipElementsAt.toMutableList()
    var offset = 0
    var elementIndex = 0
    for (skipOffset in offsets.distinct().sorted()) {
      while (elementIndex < elements.size && offset + elements[elementIndex].text.length <= skipOffset) {
        add(elements[elementIndex])
        offset += elements[elementIndex].text.length
        elementIndex++
      }
      if (elementIndex >= elements.size) {
        return@buildList
      }
      val element = elements[elementIndex]
      if (element is InlineCompletionSkipTextElement) {
        continue
      }
      val splitOffset = skipOffset - offset
      val manipulator = InlineCompletionElementManipulator.getApplicable(element)!!
      addIfNotNull(manipulator.substring(element, 0, splitOffset))
      add(InlineCompletionSkipTextElement(element.text.substring(splitOffset, splitOffset + 1)))

      val leftPart = manipulator.substring(element, splitOffset + 1, element.text.length)
      if (leftPart != null) {
        elements[elementIndex] = leftPart
      }
      else {
        elementIndex++
      }
      offset = skipOffset + 1
    }
    while (elementIndex < elements.size) {
      add(elements[elementIndex])
      elementIndex++
    }
  }

  /**
   * When we insert an open brace/quote, we'd like to insert the paired bracket/quote as well.
   * [offsets] represent all the offsets in a completion, that need to be inserted in an editor.
   *
   * The problem: when the paired bracket/quote is located on another line, that doesn't exist in an editor yet,
   * we need to insert it as well.
   * Also, we need to insert all leading whitespaces before that bracket/quote (if they are not inserted yet),
   * to be sure that cancellation of the inline completion will leave the code with correct indentation.
   *
   * This method operates over forbidden magic, doing the following:
   * * It groups [offsets] by their line.
   * * For each line we compute whether it's already existent checking [this] on instance of [InlineCompletionSkipTextElement].
   * * For each non-existent line used in insertion, we compute leading whitespaces.
   * * If line or leading whitespaces do not exist, we add them to the editor and add their offsets to new skip elements.
   *
   * @return the initial skip offsets with new skip offsets responsible to newly inserted whitespaces.
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  fun List<InlineCompletionElement>.insertOffsetsAndAdditionalLines(
    offsets: List<Int>,
    editor: Editor,
    offset: Int,
    completion: String
  ): List<InlineCompletionElement> {
    val initialOffsets = offsets
      .filter { it < completion.length && it >= 0 }
      .distinct()
      .sorted()
    if (initialOffsets.isEmpty()) {
      return this
    }

    val numberOfLines = completion.count { it == '\n' } + 1
    val lineNumberToOffsets = mapOffsetsToLineNumber(numberOfLines, completion, initialOffsets)
    val linesToInsert = computeLinesToInsertForOffsets(lineNumberToOffsets, this, completion)
    doInsertOffsetsAndAdditionalLines(editor, offset, completion, this, linesToInsert, lineNumberToOffsets)

    val newOffsets = (initialOffsets + linesToInsert.flatMap { it.whitespaceStart until it.whitespaceEnd }).distinct()
    return insertSkipElementsAt(newOffsets)
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
   * @see insertOffsetsAndAdditionalLines
   */
  private fun computeLinesToInsertForOffsets(
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
   * The final stage of [insertOffsetsAndAdditionalLines].
   *
   * Literally inserts symbols defined by skip offsets and new lines defined by [linesToInsert] at corresponding [startOffset].
   * The inserted skip offsets are located in [lineNumberToOffsets]: they are grouped by line number.
   *
   * @param linesToInsert the result of [computeLinesToInsertForOffsets]
   * @param lineNumberToOffsets the result of [mapOffsetsToLineNumber]
   */
  private fun doInsertOffsetsAndAdditionalLines(
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

  /**
   * First, see [insertOffsetsAndAdditionalLines].
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
}
