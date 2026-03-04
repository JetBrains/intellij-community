// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.fuzzyMatching

import org.jetbrains.annotations.ApiStatus

/**
 * Manages the dynamic programming matrix for the Smith-Waterman algorithm.
 *
 * Uses a space-optimized approach that maintains only two rows of the matrix
 * (current and previous) instead of the full matrix. This reduces memory usage
 * from O(m*n) to O(n) where m is pattern length and n is target length.
 *
 * For traceback, we store the full matrix since we need to reconstruct the alignment path.
 */
@ApiStatus.Internal
internal class AlignmentMatrix(
  private val patternLength: Int,
  private val targetLength: Int
) {
  // Full matrix for traceback (row-major order)
  private val matrix = IntArray((patternLength + 1) * (targetLength + 1))

  // Track maximum score and its position
  private var maxScore = 0
  private var maxRow = 0
  private var maxCol = 0

  /**
   * Computes the score for cell (i, j) in the alignment matrix.
   *
   * The Smith-Waterman algorithm uses the recurrence:
   * H(i, j) = max(0,
   *               H(i-1, j-1) + matchScore/mismatchPenalty,
   *               H(i-1, j) + gapPenalty,
   *               H(i, j-1) + gapPenalty)
   *
   * @param i Pattern position (1-based)
   * @param j Target position (1-based)
   * @param matchScore Score to add for match/mismatch at this position
   * @param gapPenalty Penalty for gaps
   * @return The computed score for this cell
   */
  fun computeCell(i: Int, j: Int, matchScore: Int, gapPenalty: Int): Int {
    val diagonal = get(i - 1, j - 1) + matchScore
    val up = get(i - 1, j) + gapPenalty
    val left = get(i, j - 1) + gapPenalty

    val score = maxOf(0, diagonal, up, left)
    set(i, j, score)

    // Track maximum score and position
    if (score > maxScore) {
      maxScore = score
      maxRow = i
      maxCol = j
    }

    return score
  }

  /**
   * Gets the score at position (i, j).
   */
  fun get(i: Int, j: Int): Int {
    return matrix[i * (targetLength + 1) + j]
  }

  /**
   * Sets the score at position (i, j).
   */
  private fun set(i: Int, j: Int, value: Int) {
    matrix[i * (targetLength + 1) + j] = value
  }

  /**
   * Returns the maximum score found in the matrix.
   */
  fun getMaxScore(): Int = maxScore

  /**
   * Returns the position (row, col) of the maximum score.
   * This is the starting point for traceback.
   */
  fun getMaxScorePosition(): Pair<Int, Int> = maxRow to maxCol

  /**
   * Performs traceback from the maximum score position to find the aligned positions.
   *
   * @param pattern The search pattern (lowercase)
   * @param target The target string (lowercase)
   * @return List of target indices where pattern characters matched
   */
  fun traceback(pattern: String, target: String): List<Int> {
    val matchedIndices = mutableListOf<Int>()
    var i = maxRow
    var j = maxCol

    // Traceback until we hit a zero score (start of local alignment)
    while (i > 0 && j > 0 && get(i, j) > 0) {
      val current = get(i, j)
      val diagonal = get(i - 1, j - 1)
      val up = get(i - 1, j)
      val left = get(i, j - 1)

      // Determine which direction we came from
      if (pattern[i - 1] == target[j - 1] && current >= diagonal) {
        // Match - came from diagonal
        matchedIndices.add(j - 1)
        i--
        j--
      } else if (current == up) {
        // Came from above (gap in target)
        i--
      } else {
        // Came from left (gap in pattern)
        j--
      }
    }

    // Return in ascending order (we collected them backwards)
    return matchedIndices.reversed()
  }
}
