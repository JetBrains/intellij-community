// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.fuzzyMatching

import org.jetbrains.annotations.ApiStatus

/**
 * Result of a fuzzy matching operation between a pattern and a target string.
 *
 * @property score Total alignment score from the Smith-Waterman algorithm.
 *                 Higher scores indicate better matches.
 * @property matchedIndices Zero-based indices of matched characters in the target string,
 *                          in ascending order. Used for highlighting matched characters in UI.
 * @property normalizedScore Score normalized to the 0.0-1.0 range based on the maximum
 *                           possible score for the given pattern length.
 */
@ApiStatus.Internal
data class FuzzyMatchResult(
  val score: Int,
  val matchedIndices: List<Int>,
  val normalizedScore: Double
) {
  companion object {
    /**
     * Constant representing no match found.
     */
    val NO_MATCH: FuzzyMatchResult = FuzzyMatchResult(0, emptyList(), 0.0)
  }

  /**
   * Returns true if this result represents a valid match (score > 0).
   */
  fun hasMatch(): Boolean = score > 0
}
