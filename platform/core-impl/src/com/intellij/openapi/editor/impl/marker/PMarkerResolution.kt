// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRangeScalarUtil

/**
 * Packs marker offsets and state into a [Long]. The offsets use the layout from [TextRangeScalarUtil].
 * An unused sign bit identifies invalid markers. Invalidation reasons remain in the [PMarkerRoot].
 * [ABSENT] and [UNRESOLVED] have no offsets.
 */
object PMarkerResolution {
  /** No cached resolution is available. This value cannot represent a marker. */
  const val UNRESOLVED: Long = -1L

  /** The marker is absent from the root. This value has no offsets. */
  const val ABSENT: Long = -2L

  private const val INVALID_FLAG: Long = 0x80000000L
  private const val STATE_MASK: Long = INVALID_FLAG or Long.MIN_VALUE

  /** Returns a packed range for a valid marker with the half-open range `[startOffset, endOffset)`. */
  fun valid(range:Long): Long = range

  /** Returns a packed range for an invalid marker with its offsets immediately before invalidation. */
  fun invalid(range: Long): Long = range or INVALID_FLAG

  fun isValid(range: Long): Boolean = range and STATE_MASK == 0L

  fun isAbsent(range: Long): Boolean = range == ABSENT

  fun startOffset(range: Long): Int = TextRangeScalarUtil.startOffset(range)

  fun endOffset(range: Long): Int = TextRangeScalarUtil.endOffset(range)

  fun toTextRange(range: Long): TextRange = TextRangeScalarUtil.create(range and INVALID_FLAG.inv())
}

/**
 * Maps cached offsets across one edit without retaining the previous root.
 *
 * @property previousIdentity the resolution identity before the edit.
 * @property unchangedPrefixEnd ranges with an old end strictly below this offset remain unchanged.
 * @property editEnd ranges with an old start strictly above this offset shift by [delta].
 * @property delta the change in the document length.
 */
internal class PMarkerResolutionUpdate(
  private val previousIdentity: Any,
  private val unchangedPrefixEnd: Int,
  private val editEnd: Int,
  private val delta: Int,
) {
  /** Advances a valid resolution, or requests full resolution when the identity or range does not match this edit. */
  fun advance(previousIdentity: Any, resolution: Long): Long {
    if (this.previousIdentity !== previousIdentity || !PMarkerResolution.isValid(resolution)) return PMarkerResolution.UNRESOLVED
    val startOffset = PMarkerResolution.startOffset(resolution)
    val endOffset = PMarkerResolution.endOffset(resolution)
    return when {
      endOffset < unchangedPrefixEnd -> resolution
      startOffset > editEnd -> {
        if (delta == 0) resolution
        else PMarkerResolution.valid(TextRangeScalarUtil.shift(resolution, delta))
      }
      else -> PMarkerResolution.UNRESOLVED
    }
  }
}
