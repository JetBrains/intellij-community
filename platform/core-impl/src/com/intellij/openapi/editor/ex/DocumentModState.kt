// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Modification-tracking state of a document's text: the [stamp]/[sequence] pair identifying a point
 * in the text's timeline, plus per-line modification flags queried through [isLineModified].
 */
@ApiStatus.Internal
interface DocumentModState {

  /**
   * Part of the document metadata tracking text timeline
   *
   * @see DocumentEx.getModificationStamp
   */
  @Contract(pure = true)
  fun stamp(): Long

  /**
   * Part of the document metadata tracking text timeline.
   * Always increases from snapshot to snapshot if text is changed.
   *
   * @see DocumentEx.getModificationSequence
   */
  @Contract(pure = true)
  fun sequence(): Int

  /**
   * Part of the document metadata tracking text timeline
   *
   * @see DocumentEx.isLineModified
   */
  @Contract(pure = true)
  fun isLineModified(line: Int): Boolean

  /**
   * Returns state with [diff] applied: line-modification tracking is updated to reflect the lines [diff]
   * touches, and [stamp]/[sequence] are taken from [diff].
   *
   * @param before the pre-patch text this instance's line-modification tracking was built against -- the same
   *             [DocumentText] that [diff] is about to be applied to via [DocumentText.applyOp]. Passing any
   *             other text silently mispairs this instance's tracked line structure with the wrong offsets.
   * @param after the post-patch text, i.e. [before] with [diff] applied; used only to cross-check the rebuilt
   *             line tracking against it, guarding against the two silently drifting apart.
   */
  @Contract(pure = true)
  fun withPatch(before: DocumentText, after: DocumentText, diff: DocumentTextPatch): DocumentModState

  @Contract(pure = true)
  fun withStamp(newStamp: Long, incrementSequence: Boolean): DocumentModState

  /**
   * Returns state with line-modification flags cleared in `[startLine, endLine)`, except lines in
   * [exceptLines] keep whatever modification flag they currently have.
   *
   * @param text the current text this instance's line-modification tracking is paired with -- the same
   *             [DocumentText] as the enclosing snapshot's. Passing any other text silently mispairs this
   *             instance's tracked line structure with the wrong line count/offsets.
   * @param endLine is exclusive. Two special values `0` and `Int.MAX_VALUE` ignoring range checks
   */
  @Contract(pure = true)
  fun withClearedLineFlags(
    text: DocumentText,
    startLine: Int,
    endLine: Int,
    exceptLines: IntArray,
  ): DocumentModState
}
