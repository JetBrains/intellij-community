// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.features.MLCompletionFeaturesCollector
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresReadLock

internal object InlineCompletionContextLogs {
  @RequiresReadLock
  @RequiresBlockingContext
  fun getFor(request: InlineCompletionRequest): List<EventPair<*>> {
    val element = if (request.startOffset == 0) null else request.file.findElementAt(request.startOffset - 1)
    val simple = captureSimple(request.file, request.editor, request.startOffset, element)
    val featureCollectorBased = MLCompletionFeaturesCollector.get(request.file.language)?.let {
      captureFeatureCollectorBased(request.file, request.editor, request.startOffset, it)
    }
    return simple + featureCollectorBased.orEmpty()
  }

  private fun captureSimple(psiFile: PsiFile, editor: Editor, offset: Int, element: PsiElement?): List<EventPair<*>> {
    val result = mutableListOf<EventPair<*>>()

    element?.let { result.add(Logs.ELEMENT_PREFIX_LENGTH with (offset - it.textOffset)) }

    val logicalPosition = editor.offsetToLogicalPosition(offset)
    val lineNumber = logicalPosition.line

    result.add(Logs.LINE_NUMBER.with(lineNumber))
    result.add(Logs.COLUMN_NUMBER.with(logicalPosition.column))
    result.add(Logs.FILE_LINE_COUNT.with(editor.document.lineCount))

    val lineStartOffset = editor.document.getLineStartOffset(lineNumber)
    val lineEndOffset = editor.document.getLineEndOffset(lineNumber)

    val linePrefix = editor.document.getText(TextRange(lineStartOffset, offset))
    val lineSuffix = editor.document.getText(TextRange(offset, lineEndOffset))

    if (linePrefix.isNotBlank()) {
      result.add(Logs.IS_WHITE_SPACE_BEFORE_CARET.with(linePrefix.last().isWhitespace()))
      val trimmedPrefix = linePrefix.trim()
      result.add(Logs.SYMBOLS_IN_LINE_BEFORE_CARET.with(trimmedPrefix.length))
      CharCategory.find(trimmedPrefix.last())?.let {
        result.add(Logs.NON_SPACE_SYMBOL_BEFORE_CARET.with(it))
      }
    }
    if (lineSuffix.isNotBlank()) {
      result.add(Logs.IS_WHITE_SPACE_AFTER_CARET.with(lineSuffix.first().isWhitespace()))
      val trimmedSuffix = lineSuffix.trim()
      result.add(Logs.SYMBOLS_IN_LINE_AFTER_CARET.with(trimmedSuffix.length))
      CharCategory.find(trimmedSuffix.first())?.let {
        result.add(Logs.NON_SPACE_SYMBOL_AFTER_CARET.with(it))
      }
    }
    val document = editor.document
    val (previousNonEmptyLineNumber, previousNonEmptyLineText) = document.findNonBlankLine(lineNumber, false)
    result.add(Logs.PREVIOUS_EMPTY_LINES_COUNT.with(lineNumber - previousNonEmptyLineNumber - 1))
    if (previousNonEmptyLineText != null) {
      result.add(Logs.PREVIOUS_NON_EMPTY_LINE_LENGTH.with(previousNonEmptyLineText.length))
    }
    val (followingNonEmptyLineNumber, followingNonEmptyLineText) = document.findNonBlankLine(lineNumber, true)
    result.add(Logs.FOLLOWING_EMPTY_LINES_COUNT.with(followingNonEmptyLineNumber - lineNumber - 1))
    if (followingNonEmptyLineText != null) {
      result.add(Logs.FOLLOWING_NON_EMPTY_LINE_LENGTH.with(followingNonEmptyLineText.length))
    }

    psiFile.findElementAt(offset - 1)?.let { result.addPsiParents(it) }
    return result
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
    val parents = element.parents(false).toList()
    Logs.PARENT_FEATURES.forEachIndexed { i, parentFeature ->
      parents.getOrNull(i)
        ?.let { add(parentFeature with it.javaClass) }
    }
  }

  private fun captureFeatureCollectorBased(file: PsiFile, editor: Editor, startOffset: Int, collector: MLCompletionFeaturesCollector): List<EventPair<*>> {
    return emptyList() // TODO
  }

  private object Logs : PhasedLogs(InlineCompletionLogsContainer.Phase.INLINE_API_STARTING) {
    val LINE_NUMBER = register(EventFields.Int("line_number"))
    val COLUMN_NUMBER = register(EventFields.Int("column_number"))
    val FILE_LINE_COUNT = register(EventFields.Int("file_line_count"))
    val SYMBOLS_IN_LINE_BEFORE_CARET = register(EventFields.Int("symbols_in_line_before_caret"))
    val SYMBOLS_IN_LINE_AFTER_CARET = register(EventFields.Int("symbols_in_line_after_caret"))
    val IS_WHITE_SPACE_BEFORE_CARET = register(EventFields.Boolean("is_white_space_before_caret"))
    val IS_WHITE_SPACE_AFTER_CARET = register(EventFields.Boolean("is_white_space_after_caret"))
    val NON_SPACE_SYMBOL_BEFORE_CARET = register(EventFields.Enum<CharCategory>("non_space_symbol_before_caret"))
    val NON_SPACE_SYMBOL_AFTER_CARET = register(EventFields.Enum<CharCategory>("non_space_symbol_after_caret"))
    val PREVIOUS_EMPTY_LINES_COUNT = register(EventFields.Int("previous_empty_lines_count"))
    val PREVIOUS_NON_EMPTY_LINE_LENGTH = register(EventFields.Int("previous_non_empty_line_length"))
    val FOLLOWING_EMPTY_LINES_COUNT = register(EventFields.Int("following_empty_lines_count"))
    val FOLLOWING_NON_EMPTY_LINE_LENGTH = register(EventFields.Int("following_non_empty_line_length"))
    val PARENT_FEATURES = listOf("first", "second", "third", "forth", "fifth").map {
      register(EventFields.Class("${it}_parent"))
    }
    val ELEMENT_PREFIX_LENGTH = register(EventFields.Int("element_prefix_length"))
  }

  internal class CollectorExtension : InlineCompletionSessionLogsEP {
    override val fields: List<PhasedLogs> = listOf(Logs)
  }
}
