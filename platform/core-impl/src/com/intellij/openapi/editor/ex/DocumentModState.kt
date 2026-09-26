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
   * Returns state with [op] applied. For a [DocumentTextPatch], [before] must be the text this instance's
   * line-modification tracking was built against, and [after] must be [before] with the patch applied.
   * For [DocumentOp.UnmodifiedLines], [before] must be the current text paired with this instance.
   */
  @Contract(pure = true)
  fun applyOp(before: DocumentText, after: DocumentText, op: DocumentOp): DocumentModState
}
