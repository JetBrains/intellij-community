// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.filters.Filter
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.editor.impl.FrozenDocument
import org.jetbrains.annotations.ApiStatus

/**
 * Applies the filter to the given document range.
 *
 * Note that some filters may require a read action to run ([com.intellij.execution.filters.CompositeFilter], for example).
 *
 * To enable cancellation, the caller must call some cancellation check function in [onEach].
 * For example, if executed under a read action, call [com.intellij.openapi.progress.ProgressManager.checkCanceled].
 *
 * Exceptions thrown by the filters are caught and logged. They don't terminate processing.
 *
 * @param document the document, must be a snapshot so that filters can be run safely in background
 * @param startLineInclusive the first line (0-based) to apply the filter
 * @param endLineInclusive the last line (0-based) to apply the filter, inclusive, can be smaller than [startLineInclusive] to reverse the iteration direction
 * @param onEach the function that is called for every line (regardless of whether the filter returned any result)
 */
@ApiStatus.Internal
fun Filter.applyToLineRange(document: FrozenDocument, startLineInclusive: Int, endLineInclusive: Int, onEach: (FilterApplyResult) -> Unit) {
  val lineCount = document.lineCount
  require(startLineInclusive in 0 until lineCount) { "Invalid startLine=$startLineInclusive, lineCount=$lineCount" }
  require(endLineInclusive in 0 until lineCount) { "Invalid endLine=$endLineInclusive, lineCount=$lineCount" }
  val range = if (startLineInclusive <= endLineInclusive) {
    startLineInclusive..endLineInclusive
  }
  else {
    startLineInclusive downTo endLineInclusive
  }
  for (lineNumber in range) {
    val lineStart = document.getLineStartOffset(lineNumber)
    val lineText = EditorHyperlinkSupport.getLineText(document, lineNumber, true)
    val lineEnd = lineStart + lineText.length
    val result = applyToLine(lineText, lineEnd)
    onEach(FilterApplyResult(lineNumber, result))
  }
}

private fun Filter.applyToLine(lineText: String, lineEnd: Int): Filter.Result? {
  return runCatching {
    applyFilter(lineText, lineEnd)?.let {
      AsyncFilterRunner.checkRange(this, lineEnd, it)
    }
  }.getOrLogException { exception ->
    LOG.error("Filter threw an exception, filter = $this", exception)
  }
}

/**
 * A result of processing a single line.
 *
 * @param lineNumber the line (0-based) that was just processed, can be used for progress reporting, for example
 * @param filterResult the filtering result, if any
 */
@ApiStatus.Internal
data class FilterApplyResult(
  val lineNumber: Int,
  val filterResult: Filter.Result?,
)

private val LOG = fileLogger()
