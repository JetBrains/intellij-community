package com.intellij.codeInsight.codeVision

/**
 * Describes the position of entry relative to target range
 */
enum class CodeVisionAnchorKind(val key: String) {
  /**
   * Above the target range
   */
  Top("codeLens.entry.position.top"),

  /**
   * After end of line with target range
   */
  Right("codeLens.entry.position.right"),

  /**
   * On the same line as target range, near the scrollbar
   */
  NearScroll("codeLens.entry.position.nearScroll"),

  /**
   * In any empty space near the target range
   */
  EmptySpace("codeLens.entry.position.emptySpace"),

  /**
   * Use the global default value from settings
   */
  Default("codeLens.entry.position.default");
}