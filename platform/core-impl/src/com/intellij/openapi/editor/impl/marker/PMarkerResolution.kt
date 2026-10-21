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

  /** Returns a valid marker with the half-open range `[startOffset, endOffset)`. */
  fun valid(startOffset: Int, endOffset: Int): Long = TextRangeScalarUtil.toScalarRange(startOffset, endOffset)

  /** Returns an invalid marker with its offsets immediately before invalidation. */
  fun invalid(startOffset: Int, endOffset: Int): Long = valid(startOffset, endOffset) or INVALID_FLAG

  fun isValid(resolution: Long): Boolean = resolution and STATE_MASK == 0L

  fun isAbsent(resolution: Long): Boolean = resolution == ABSENT

  fun startOffset(resolution: Long): Int = TextRangeScalarUtil.startOffset(resolution)

  fun endOffset(resolution: Long): Int = TextRangeScalarUtil.endOffset(resolution)

  fun toTextRange(resolution: Long): TextRange = TextRangeScalarUtil.create(resolution and INVALID_FLAG.inv())
}
