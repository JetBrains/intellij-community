// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.fuzzyMatching

import org.jetbrains.annotations.ApiStatus

/**
 * Scoring parameters for Smith-Waterman fuzzy matching algorithm.
 * These parameters control how matches, gaps, and position-based bonuses are scored.
 *
 * Default values are based on FZF's scoring system.
 */
@ApiStatus.Internal
data class ScoringParameters(
  /**
   * Score awarded for matching characters.
   * FZF default: 16
   */
  val matchScore: Int = 16,

  /**
   * Penalty for mismatched characters.
   * FZF skips mismatches rather than penalizing them, so default is 0.
   */
  val mismatchPenalty: Int = 0,

  /**
   * Penalty for gaps in the alignment.
   * FZF uses variable gap penalties; we use a simple constant.
   * Default: -1
   */
  val gapPenalty: Int = -1,

  /**
   * Bonus for matching the first character of the target string.
   * Encourages matches at the beginning.
   * Default: 8
   */
  val firstCharBonus: Int = 8,

  /**
   * Bonus for consecutive character matches.
   * Encourages contiguous matching sequences.
   * Default: 6
   */
  val consecutiveBonus: Int = 6,

  /**
   * Bonus for matching a character that comes after an uppercase letter
   * in a camelCase identifier (e.g., "CF" matching "ClassFile").
   * Default: 7
   */
  val camelCaseBonus: Int = 7,

  /**
   * Bonus for matching a character that comes after a separator
   * (such as /, _, -, ., or space).
   * Encourages matching at word boundaries.
   * Default: 8
   */
  val separatorBonus: Int = 8
)
