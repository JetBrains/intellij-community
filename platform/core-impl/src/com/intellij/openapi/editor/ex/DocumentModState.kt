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
   * Returns state with [patch] applied: line-modification tracking is updated to reflect the lines [patch]
   * touches, and [stamp]/[sequence] are taken from [patch].
   *
   * @param text the pre-patch text this instance's line-modification tracking was built against -- the same
   *             [DocumentText] that [patch] is about to be applied to via [DocumentText.withPatch]. Passing any
   *             other text silently mispairs this instance's tracked line structure with the wrong offsets.
   */
  @Contract(pure = true)
  fun withPatch(text: DocumentText, patch: DocumentTextPatch): DocumentModState

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

  /**
   * Returns state with [stamp]/[sequence] taken from [other], keeping this instance's own
   * line-modification tracking: it stays paired with the text this instance was built against,
   * never with [other]'s, which the caller is about to discard.
   *
   * @see DocumentSnapshot.withMetadata
   */
  @Contract(pure = true)
  fun withMetadata(other: DocumentModState): DocumentModState

  /**
   * Number of lines this instance's line-modification tracking currently reflects, or `null` if that tracking
   * hasn't been built yet (no edit or line-flag operation has touched this instance since it was created fresh).
   *
   * For debug-assertion cross-checks against [DocumentText.lineCount] only, guarding against [withPatch]'s
   * independently-maintained line structure silently drifting from the paired [DocumentText]'s -- see
   * [DocumentSnapshot.withPatch]. Not a general-purpose line-count query; use [DocumentText.lineCount] for that.
   */
  @Contract(pure = true)
  fun lineCount(): Int?
}
