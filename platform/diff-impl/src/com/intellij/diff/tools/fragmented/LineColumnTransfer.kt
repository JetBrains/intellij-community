// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented

import com.intellij.diff.comparison.isWhiteSpaceCodePoint
import com.intellij.openapi.util.text.Strings
import org.jetbrains.annotations.ApiStatus

/**
 * Transfers a column between two versions of one line.
 *
 * If adjusts column position, ignoring the differences in whitespaces.
 * It returns null if there are non-whitespace differences.
 * It matches non-whitespace symbols to their counterparts.
 *
 * A whitespace right after a non-whitespace character stays right after it.
 * A whitespace right before a non-whitespace character stays right before it.
 * A whitespace inside a whitespace run (sequence of whitespace symbols) keeps its distance from the start of the run, if possible.
 *
 * All whitespace symbols are a single codepoint, so we can use simple character iteration.
 */
@ApiStatus.Internal
object LineColumnTransfer {
  @JvmStatic
  fun transferColumn(from: CharSequence, to: CharSequence, column: Int): Int? {
    if (column < 0 || column > from.length) return null
    if (!Strings.equalsIgnoreWhitespaces(from, to)) return null

    var indexFrom = 0
    var indexTo = 0 // right after the non-whitespace characters matched so far
    while (indexFrom < column) {
      val c = from[indexFrom]
      if (isWhiteSpaceCodePoint(c.code)) {
        indexFrom++
        continue
      }
      while (indexTo < to.length && isWhiteSpaceCodePoint(to[indexTo].code)) indexTo++
      if (indexTo == to.length || to[indexTo] != c) return null
      indexFrom++
      indexTo++
    }

    // the column is right after a non-whitespace character: it stays right after the same character
    if (column > 0 && !isWhiteSpaceCodePoint(from[column - 1].code)) return indexTo

    var runEnd = indexTo
    while (runEnd < to.length && isWhiteSpaceCodePoint(to[runEnd].code)) runEnd++
    // the column is right before a non-whitespace character, after a whitespace run: it stays right before the same character
    if (column < from.length && !isWhiteSpaceCodePoint(from[column].code)) return runEnd

    // the column is inside a whitespace run: it keeps its distance from the start of the run, up to the end of the run
    var runStart = column
    while (runStart > 0 && isWhiteSpaceCodePoint(from[runStart - 1].code)) runStart--
    return minOf(indexTo + (column - runStart), runEnd)
  }
}
