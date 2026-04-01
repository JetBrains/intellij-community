// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.fuzzyMatching

import com.intellij.util.fuzzyMatching.FuzzyMatchResult
import org.jetbrains.annotations.ApiStatus

/**
 * Implementation of the Smith-Waterman local sequence alignment algorithm
 * for fuzzy string matching, adapted for file/code search with FZF-style scoring.
 *
 * The algorithm finds the best local alignment between a pattern (search query)
 * and a target string (e.g., filename), incorporating position-based bonuses
 * for consecutive matches, camelCase boundaries, and separators.
 *
 * Key differences from standard Smith-Waterman:
 * - Case-insensitive matching
 * - Position-based bonuses (first char, consecutive, camelCase, separators)
 * - Returns matched character indices for highlighting
 * - Normalized scoring for easier thresholding
 *
 * @see <a href="https://github.com/junegunn/fzf">FZF fuzzy finder</a>
 */
@ApiStatus.Internal
object SmithWatermanAlgorithm {
  /**
   * Performs Smith-Waterman local sequence alignment between pattern and target.
   *
   * Algorithm:
   * 1. Build dynamic programming matrix with scoring
   * 2. Apply bonuses for position-based features (first char, consecutive, camelCase, separators)
   * 3. Traceback from highest score to find matched positions
   *
   * @param pattern Search query (e.g., "gcf")
   * @param target String to match against (e.g., "GotoClassFile.kt")
   * @param params Scoring parameters (match, gap, bonuses)
   * @return FuzzyMatchResult with score and matched character positions
   */
  fun match(
    pattern: String,
    target: String,
    params: ScoringParameters = ScoringParameters()
  ): FuzzyMatchResult {
    if (pattern.isEmpty()) return FuzzyMatchResult.NO_MATCH
    if (target.isEmpty()) return FuzzyMatchResult.NO_MATCH

    val patternLower = pattern.lowercase()
    val targetLower = target.lowercase()

    // Early check: pattern must be shorter than target for meaningful match
    if (pattern.length > target.length) {
      return FuzzyMatchResult.NO_MATCH
    }

    // Build alignment matrix
    val matrix = AlignmentMatrix(pattern.length, target.length)

    // Compute scores with bonuses
    for (i in 1..pattern.length) {
      for (j in 1..target.length) {
        val patternChar = patternLower[i - 1]
        val targetChar = targetLower[j - 1]

        val isMatch = patternChar == targetChar
        val baseScore = if (isMatch) params.matchScore else params.mismatchPenalty

        // Apply position-based bonuses for matches
        val bonusScore = if (isMatch) {
          calculateBonuses(target, patternLower, targetLower, j - 1, i == 1, matrix, i, j, params)
        } else 0

        matrix.computeCell(i, j, baseScore + bonusScore, params.gapPenalty)
      }
    }

    val maxScore = matrix.getMaxScore()

    // If no match found, return NO_MATCH
    if (maxScore == 0) {
      return FuzzyMatchResult.NO_MATCH
    }

    // Traceback to find matched positions
    val matchedIndices = matrix.traceback(patternLower, targetLower)

    // Normalize score (0.0-1.0) based on pattern length
    val maxPossibleScore = calculateMaxPossibleScore(pattern.length, params)
    val lengthAdjustedScore = maxScore * 100 - target.length

    val normalizedScore = if (maxPossibleScore > 0) {
      (lengthAdjustedScore.toDouble() / maxPossibleScore).coerceIn(0.0, 1.0)
    } else 0.0

    return FuzzyMatchResult(lengthAdjustedScore, matchedIndices, normalizedScore)
  }

  /**
   * Calculates position-based bonuses for a matched character.
   *
   * Bonuses are applied for:
   * - First character of the target string
   * - Consecutive matches (when previous pattern/target chars also matched)
   * - CamelCase boundaries (lowercase followed by uppercase)
   * - Separator boundaries (/, _, -, ., space)
   *
   * @param target Original target string (with case)
   * @param patternLower Lowercase version of pattern
   * @param targetLower Lowercase version of target
   * @param pos Position in target string (0-based)
   * @param isFirstPatternChar Whether this is the first character of the pattern
   * @param matrix The alignment matrix to check for consecutive matches
   * @param i Current pattern position (1-based)
   * @param j Current target position (1-based)
   * @param params Scoring parameters
   * @return Total bonus score to add
   */
  private fun calculateBonuses(
    target: String,
    patternLower: String,
    targetLower: String,
    pos: Int,
    isFirstPatternChar: Boolean,
    matrix: AlignmentMatrix,
    i: Int,
    j: Int,
    params: ScoringParameters
  ): Int {
    var bonus = 0

    // First character bonus (matching at start of target)
    if (isFirstPatternChar && pos == 0) {
      bonus += params.firstCharBonus
    }

    // Consecutive match bonus (previous diagonal cell had a match)
    // This indicates the previous pattern char matched the previous target char
    if (i > 1 && j > 1 && patternLower[i - 2] == targetLower[j - 2] && matrix.get(i - 1, j - 1) > 0) {
      bonus += params.consecutiveBonus
    }

    // Position-based bonuses (check previous character context)
    if (pos > 0) {
      val prevChar = target[pos - 1]
      val currentChar = target[pos]

      // CamelCase bonus (lowercase -> uppercase transition)
      // Example: "CF" in "ClassFile"
      if (prevChar.isLowerCase() && currentChar.isUpperCase()) {
        bonus += params.camelCaseBonus
      }

      // Separator bonus (match after separator character)
      // Example: "f" after "/" in "src/File.kt"
      if (prevChar in "/_-. ") {
        bonus += params.separatorBonus
      }
    }

    return bonus
  }

  /**
   * Calculates the maximum possible score for a pattern of given length.
   * This is used to normalize scores to the 0.0-1.0 range.
   *
   * Maximum assumes:
   * - All characters match
   * - First character gets first char bonus
   * - All subsequent characters get consecutive bonus
   * - No gap penalties applied
   *
   * @param patternLength Length of the search pattern
   * @param params Scoring parameters
   * @return Maximum possible score
   */
  private fun calculateMaxPossibleScore(
    patternLength: Int,
    params: ScoringParameters
  ): Int {
    if (patternLength == 0) return 0

    // First character: match + first char bonus
    var maxScore = params.matchScore + params.firstCharBonus

    // Remaining characters: match + consecutive bonus (best case)
    if (patternLength > 1) {
      maxScore += (patternLength - 1) * (params.matchScore + params.consecutiveBonus)
    }

    return maxScore * 100
  }
}
