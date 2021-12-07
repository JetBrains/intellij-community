package com.intellij.codeInsight.codeVision

/**
 * Describes the position of entry relative to target range
 */
enum class CodeVisionAnchorKind {
  /**
   * Above the target range
   */
  Top,

  /**
   * After end of line with target range
   */
  Right,

  /**
   * On the same line as target range, near the scrollbar
   */
  NearScroll,

  /**
   * In any empty space near the target range
   */
  EmptySpace,

  /**
   * Use the global default value from settings
   */
  Default;
}