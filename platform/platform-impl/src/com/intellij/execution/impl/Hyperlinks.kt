@file:ApiStatus.Experimental
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.filters.Filter
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.getOrHandleException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface HypertextInput {
  val lineCount: Int
  fun getLineStartOffset(lineIndex: Int): Int
  fun getLineText(lineIndex: Int): String
}

/**
 * Applies the filter to the given text range.
 *
 * Note that some filters may require a read action to run ([com.intellij.execution.filters.CompositeFilter], for example).
 *
 * To enable cancellation, the caller must call some cancellation check function in [onEach].
 * For example, if executed under a read action, call [com.intellij.openapi.progress.ProgressManager.checkCanceled].
 *
 * Exceptions thrown by the filters are caught and logged. They don't terminate processing.
 *
 * @param input the input text, must be immutable and thread-safe so that filters can be run safely in background
 * @param startLineInclusive the first line (0-based) to apply the filter
 * @param endLineInclusive the last line (0-based) to apply the filter, inclusive, can be smaller than [startLineInclusive] to reverse the iteration direction
 * @param onEach the function that is called for every line (regardless of whether the filter returned any result)
 */
@ApiStatus.Experimental
fun Filter.applyToLineRange(input: HypertextInput, startLineInclusive: Int, endLineInclusive: Int, onEach: (FilterApplyResult) -> Unit) {
  val lineCount = input.lineCount
  require(startLineInclusive in 0 until lineCount) { "Invalid startLine=$startLineInclusive, lineCount=$lineCount" }
  require(endLineInclusive in 0 until lineCount) { "Invalid endLine=$endLineInclusive, lineCount=$lineCount" }
  val range = if (startLineInclusive <= endLineInclusive) {
    startLineInclusive..endLineInclusive
  }
  else {
    startLineInclusive downTo endLineInclusive
  }
  for (lineNumber in range) {
    val lineStart = input.getLineStartOffset(lineNumber)
    val lineText = input.getLineText(lineNumber)
    val lineEnd = lineStart + lineText.length
    val result = applyToLine(lineText, lineEnd)
    onEach(FilterApplyResultImpl(lineNumber, result))
  }
}

private fun Filter.applyToLine(lineText: String, lineEnd: Int): Filter.Result? {
  return runCatching {
    applyFilter(lineText, lineEnd)?.let {
      AsyncFilterRunner.checkRange(this, lineEnd, it)
    }
  }.getOrHandleException { exception ->
    LOG.error("Filter threw an exception, filter = $this", exception)
  }
}

@ApiStatus.Experimental
interface FilterApplyResult {
  val lineNumber: Int
  val filterResult: Filter.Result?
}

/**
 * A result of processing a single line.
 *
 * @param lineNumber the line (0-based) that was just processed, can be used for progress reporting, for example
 * @param filterResult the filtering result, if any
 */
private data class FilterApplyResultImpl(
  override val lineNumber: Int,
  override val filterResult: Filter.Result?,
) : FilterApplyResult

private val LOG = fileLogger()
