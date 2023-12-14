// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object InlineContextFeatures {

  fun capture(psiFile: PsiFile, editor: Editor, offset: Int): List<EventPair<*>> {
    val contextFeatures = mutableListOf<EventPair<*>>()
    try {
      doCapture(psiFile, editor, offset, contextFeatures)
    } catch (e: Exception) {
      LOG.error(e)
    }
    return contextFeatures
  }

  private fun doCapture(psiFile: PsiFile, editor: Editor, offset: Int, contextFeatures: MutableList<EventPair<*>>) {
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
    if (previousNonEmptyLineText != null) {
      contextFeatures.add(PREVIOUS_NON_EMPTY_LINE_LENGTH.with(previousNonEmptyLineText.length))
    }
    val (followingNonEmptyLineNumber, followingNonEmptyLineText) = document.findNonBlankLine(lineNumber, true)
    contextFeatures.add(FOLLOWING_EMPTY_LINES_COUNT.with(followingNonEmptyLineNumber - lineNumber - 1))
    if (followingNonEmptyLineText != null) {
      contextFeatures.add(FOLLOWING_NON_EMPTY_LINE_LENGTH.with(followingNonEmptyLineText.length))
    }

    psiFile.findElementAt(offset)?.let { contextFeatures.addPsiParents(it) }
    contextFeatures.addTypingFeatures()
  }

  private fun Document.findNonBlankLine(lineNumber: Int, following: Boolean): Pair<Int, String?> {
    val delta = if (following) 1 else -1
    var n = lineNumber
    var text: String? = null
    while (n in 0..<lineCount && text == null) {
      n += delta
      text = getNonBlankLineOrNull(n)
    }
    return n to text
  }

  private fun Document.getNonBlankLineOrNull(line: Int): String? {
    if (line !in 0..<lineCount) return null
    val res = getText(TextRange(getLineStartOffset(line), getLineEndOffset(line)))
    return res.trim().ifEmpty { null }
  }

  private fun MutableList<EventPair<*>>.addPsiParents(element: PsiElement) {
    // First parent is always referenceExpression
    val curParent: PsiElement = element.parent ?: return
    val firstParent = curParent.parent
    if (firstParent == null || firstParent is PsiFileSystemItem) return
    add(FIRST_PARENT.with(firstParent::class.java))
    val secondParent = firstParent.parent
    if (secondParent == null || secondParent is PsiFileSystemItem) return
    add(SECOND_PARENT.with(secondParent::class.java))
  }

  private fun MutableList<EventPair<*>>.addTypingFeatures() {
    val typingSpeedTracker = TypingSpeedTracker.getInstance()
    val timeSinceLastTyping = typingSpeedTracker.getTimeSinceLastTyping()
    if (timeSinceLastTyping != null) {
      add(TIME_SINCE_LAST_TYPING.with(timeSinceLastTyping))
      addAll(typingSpeedTracker.getTypingSpeedEventPairs())
    }
  }

  val KEY: Key<List<EventPair<*>>> = Key.create("inline_context_features")
  private val LOG = logger<InlineContextFeatures>()

  val LINE_NUMBER = EventFields.Int("line_number")
  val COLUMN_NUMBER = EventFields.Int("column_number")
  val SYMBOLS_IN_LINE_BEFORE_CARET = EventFields.Int("symbols_in_line_before_caret")
  val SYMBOLS_IN_LINE_AFTER_CARET = EventFields.Int("symbols_in_line_after_caret")
  val IS_WHITE_SPACE_BEFORE_CARET = EventFields.Boolean("is_white_space_before_caret")
  val IS_WHITE_SPACE_AFTER_CARET = EventFields.Boolean("is_white_space_after_caret")
  val NON_SPACE_SYMBOL_BEFORE_CARET = EventFields.Enum("non_space_symbol_before_caret", CharCategory::class.java)
  val NON_SPACE_SYMBOL_AFTER_CARET = EventFields.Enum("non_space_symbol_after_caret", CharCategory::class.java)
  val PREVIOUS_EMPTY_LINES_COUNT = EventFields.Int("previous_empty_lines_count")
  val PREVIOUS_NON_EMPTY_LINE_LENGTH = EventFields.Int("previous_non_empty_line_length")
  val FOLLOWING_EMPTY_LINES_COUNT = EventFields.Int("following_empty_lines_count")
  val FOLLOWING_NON_EMPTY_LINE_LENGTH = EventFields.Int("following_non_empty_line_length")
  val FIRST_PARENT = EventFields.Class("first_parent")
  val SECOND_PARENT = EventFields.Class("second_parent")
  val TIME_SINCE_LAST_TYPING = EventFields.Long("time_since_last_typing")
}