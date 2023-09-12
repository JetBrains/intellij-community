// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
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

    val textBeforeCaret = editor.document.getText(TextRange(lineStartOffset, offset))
    val textAfterCaret = editor.document.getText(TextRange(offset, lineEndOffset))

    val symbolsInLineBeforeCaret = textBeforeCaret.trim().length
    val symbolsInLineAfterCaret = textAfterCaret.trim().length

    contextFeatures.add(SYMBOLS_IN_LINE_BEFORE_CARET.with(symbolsInLineBeforeCaret))
    contextFeatures.add(SYMBOLS_IN_LINE_AFTER_CARET.with(symbolsInLineAfterCaret))
  }

  fun getEventPair(triggerFeatures: List<EventPair<*>>) = CONTEXT_FEATURES.with(ObjectEventData(triggerFeatures))

  private val LINE_NUMBER = Int("line_number")
  private val COLUMN_NUMBER = Int("column_number")
  private val SYMBOLS_IN_LINE_BEFORE_CARET = Int("symbols_in_line_before_caret")
  private val SYMBOLS_IN_LINE_AFTER_CARET = Int("symbols_in_line_after_caret")

  val CONTEXT_FEATURES = ObjectEventField(
    "context_features",
    LINE_NUMBER,
    COLUMN_NUMBER,
    SYMBOLS_IN_LINE_BEFORE_CARET,
    SYMBOLS_IN_LINE_AFTER_CARET
  )
}