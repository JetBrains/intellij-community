// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange

internal object InlineContextFeatures {
  fun capture(editor: Editor, offset: Int, contextFeatures: MutableList<EventPair<*>>) {
    val logicalPosition = editor.offsetToLogicalPosition(offset)
    val lineNumber = logicalPosition.line
    val columnNumber = logicalPosition.column

    contextFeatures.add(LINE_NUMBER.with(lineNumber))
    contextFeatures.add(COLUMN_NUMBER.with(columnNumber))

    val lineStartOffset = editor.document.getLineStartOffset(lineNumber)
    val lineEndOffset = editor.document.getLineEndOffset(lineNumber)

    val linePrefix = editor.document.getText(TextRange(lineStartOffset, offset))
    val lineSuffix = editor.document.getText(TextRange(offset, lineEndOffset))

    if (linePrefix.isNotBlank()) {
      contextFeatures.add(IS_WHITE_SPACE_BEFORE_CARET.with(linePrefix.last().isWhitespace()))
      val trimmedPrefix = linePrefix.trim()
      contextFeatures.add(SYMBOLS_IN_LINE_BEFORE_CARET.with(trimmedPrefix.length))
      CharCategory.find(trimmedPrefix.last())?.let {
        contextFeatures.add(NON_SPACE_SYMBOL_BEFORE_CARET.with(it))
      }
    }
    if (lineSuffix.isNotBlank()) {
      contextFeatures.add(IS_WHITE_SPACE_AFTER_CARET.with(lineSuffix.first().isWhitespace()))
      val trimmedSuffix = lineSuffix.trim()
      contextFeatures.add(SYMBOLS_IN_LINE_AFTER_CARET.with(trimmedSuffix.length))
      CharCategory.find(trimmedSuffix.last())?.let {
        contextFeatures.add(NON_SPACE_SYMBOL_AFTER_CARET.with(it))
      }
    }
    val document = editor.document
    val (previousNonEmptyLineNumber, previousNonEmptyLineText) = document.findNonBlankLine(lineNumber, false)
    contextFeatures.add(PREVIOUS_EMPTY_LINES_COUNT.with(lineNumber - previousNonEmptyLineNumber - 1))
    if (!previousNonEmptyLineText.isNullOrBlank()) {
      contextFeatures.add(PREVIOUS_NON_EMPTY_LINE_LENGTH.with(previousNonEmptyLineText.length))
    }
    val (followingNonEmptyLineNumber, followingNonEmptyLineText) = document.findNonBlankLine(lineNumber, true)
    contextFeatures.add(FOLLOWING_EMPTY_LINES_COUNT.with(followingNonEmptyLineNumber - lineNumber - 1))
    if (!followingNonEmptyLineText.isNullOrBlank()) {
      contextFeatures.add(FOLLOWING_NON_EMPTY_LINE_LENGTH.with(followingNonEmptyLineText.length))
    }
  }

  private fun Document.findNonBlankLine(lineNumber: Int, following: Boolean): Pair<Int, String?> {
    val delta = if (following) 1 else -1
    var n = lineNumber
    var text: String? = null
    while (n in 0..<lineCount && text.isNullOrBlank()) {
      n += delta
      text = getLineText(n).trim()
    }
    return n to text
  }

  private fun Document.getLineText(line: Int) = getText(TextRange(getLineStartOffset(line), getLineEndOffset(line)))

  fun getEventPair(triggerFeatures: List<EventPair<*>>) = CONTEXT_FEATURES.with(ObjectEventData(triggerFeatures))

  private val LINE_NUMBER = EventFields.Int("line_number")
  private val COLUMN_NUMBER = EventFields.Int("column_number")
  private val SYMBOLS_IN_LINE_BEFORE_CARET = EventFields.Int("symbols_in_line_before_caret")
  private val SYMBOLS_IN_LINE_AFTER_CARET = EventFields.Int("symbols_in_line_after_caret")
  private val IS_WHITE_SPACE_BEFORE_CARET = EventFields.Boolean("is_white_space_before_caret")
  private val IS_WHITE_SPACE_AFTER_CARET = EventFields.Boolean("is_white_space_after_caret")
  private val NON_SPACE_SYMBOL_BEFORE_CARET = EventFields.Enum("non_space_symbol_before_caret", CharCategory::class.java)
  private val NON_SPACE_SYMBOL_AFTER_CARET = EventFields.Enum("non_space_symbol_after_caret", CharCategory::class.java)
  private val PREVIOUS_EMPTY_LINES_COUNT = EventFields.Int("previous_empty_lines_count")
  private val PREVIOUS_NON_EMPTY_LINE_LENGTH = EventFields.Int("previous_non_empty_line_length")
  private val FOLLOWING_EMPTY_LINES_COUNT = EventFields.Int("following_empty_lines_count")
  private val FOLLOWING_NON_EMPTY_LINE_LENGTH = EventFields.Int("following_non_empty_line_length")

  val CONTEXT_FEATURES = ObjectEventField(
    "context_features",
    LINE_NUMBER,
    COLUMN_NUMBER,
    SYMBOLS_IN_LINE_BEFORE_CARET,
    SYMBOLS_IN_LINE_AFTER_CARET,
    IS_WHITE_SPACE_BEFORE_CARET,
    IS_WHITE_SPACE_AFTER_CARET,
    NON_SPACE_SYMBOL_BEFORE_CARET,
    NON_SPACE_SYMBOL_AFTER_CARET,
    PREVIOUS_EMPTY_LINES_COUNT,
    PREVIOUS_NON_EMPTY_LINE_LENGTH,
    FOLLOWING_EMPTY_LINES_COUNT,
    FOLLOWING_NON_EMPTY_LINE_LENGTH
  )
}