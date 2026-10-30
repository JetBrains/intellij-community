// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.util.TextRangeScalarUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Transforms one marker in response to a logical document edit.
 *
 * Implementations return offsets or a predefined invalidation result. The root preserves the marker metadata.
 */
fun interface MarkerPolicy {
  /** True when this policy uses persistent line translation for large document replacements. */
  val isPersistent: Boolean
    get() = false

  /**
   * Transforms [entry] for [patch]. The root applies all pending ancestor shifts before this call.
   * [MarkerEntry.nodeStart] and [MarkerEntry.nodeEnd] therefore use [beforeText] coordinates.
   */
  fun transform(
      entry: MarkerEntry,
      patch: DocumentPatch,
      beforeText: DocumentText,
      afterText: DocumentText,
  ): MarkerTransformResult

  /** Applies policy rules after a text move retargets [entry]. */
  fun afterRetarget(entry: MarkerEntry, text: DocumentText): MarkerTransformResult = MarkerTransformResult(entry)
}

/**
 * Packs the resulting offsets into a [Long] with the layout from [TextRangeScalarUtil].
 * Negative values identify predefined invalidation reasons and contain no offsets.
 * The root retains the input offsets when a policy invalidates a marker.
 */
@JvmInline
value class MarkerTransformResult private constructor(val range: Long) {
  constructor(startOffset: Int, endOffset: Int) : this(TextRangeScalarUtil.toScalarRange(startOffset, endOffset))

  constructor(entry: MarkerEntry) : this(entry.nodeStart, entry.nodeEnd)

  constructor(error: MarkerTransformError) : this(-error.ordinal.toLong() - 1)

  val isValid: Boolean
    get() = range >= 0

  val startOffset: Int
    get() = TextRangeScalarUtil.startOffset(range)

  val endOffset: Int
    get() = TextRangeScalarUtil.endOffset(range)

  val errorReason: String?
    get() = if (isValid) null else MarkerTransformError.entries[(-range - 1).toInt()].message

  /** Applies valid offsets to [entry] and preserves its metadata. */
  internal fun applyTo(entry: MarkerEntry): MarkerEntry {
    check(isValid)
    return if (startOffset == entry.nodeStart && endOffset == entry.nodeEnd) entry
    else entry.copy(nodeStart = startOffset, nodeEnd = endOffset)
  }
}

/** Defines the reasons that a marker policy can invalidate a marker. */
enum class MarkerTransformError(val message: String) {
  INVALIDATED_BY_EDIT("Marker was invalidated by a document edit"),
  EMPTY_FOLD_REGION("The fold region became empty"),
  EMPTY_CUSTOM_FOLD_REGION("The custom fold region became empty"),
  CUSTOM_FOLD_REGION_OUTSIDE_LINE_BOUNDARIES("The custom fold region left its line boundaries"),
  INVALID_CUSTOM_WRAP_OFFSET("The custom wrap reached an invalid offset"),
  INLINE_INLAY_INSIDE_SURROGATE_PAIR("The inline inlay reached a surrogate pair"),
}

/**
 * Standard IntelliJ range-marker transformation policy.
 */
object DefaultMarkerPolicy : MarkerPolicy {
  override fun transform(
      entry: MarkerEntry,
      patch: DocumentPatch,
      beforeText: DocumentText,
      afterText: DocumentText,
  ): MarkerTransformResult {
    return if (entry.nodeStart == entry.nodeEnd) {
      transformPoint(entry, patch)
    }
    else {
      transformRange(entry, patch)
    }
  }

  private fun transformPoint(entry: MarkerEntry, patch: DocumentPatch): MarkerTransformResult {
    val point = entry.nodeStart
    val editStart = patch.startOffset()
    val editEnd = patch.endOffset()
    val oldLength = editEnd - editStart
    val newLength = patch.newFragment().length

    if (editStart < point && point < editEnd) return MarkerTransformResult(MarkerTransformError.INVALIDATED_BY_EDIT)

    if (oldLength == 0 && editStart == point && MarkerSpec.isGreedyToRight(entry.spec)) {
      return MarkerTransformResult(point, point + newLength)
    }

    if (oldLength == 0 && editStart == point && MarkerSpec.isStickingToRight(entry.spec)) {
      val shifted = point + newLength
      return MarkerTransformResult(shifted, shifted)
    }

    if (point > editEnd || point == editEnd && oldLength > 0) {
      val shifted = point + newLength - oldLength
      return MarkerTransformResult(shifted, shifted)
    }

    return MarkerTransformResult(entry)
  }

  private fun transformRange(entry: MarkerEntry, patch: DocumentPatch): MarkerTransformResult {
    val startOffset = entry.nodeStart
    val endOffset = entry.nodeEnd
    val editStart = patch.startOffset()
    val editEnd = patch.endOffset()
    val newLength = patch.newFragment().length
    val delta = newLength - (editEnd - editStart)

    if (editStart > endOffset) return MarkerTransformResult(entry)
    if (!MarkerSpec.isGreedyToRight(entry.spec) && endOffset == editStart) {
      if (editStart == editEnd && patch.originStartOffset() < editStart) {
        return MarkerTransformResult(startOffset, endOffset + newLength)
      }
      return MarkerTransformResult(entry)
    }
    if (startOffset > editEnd) {
      return MarkerTransformResult(startOffset + delta, endOffset + delta)
    }
    if (!MarkerSpec.isGreedyToLeft(entry.spec) && startOffset == editEnd) {
      if (editStart == editEnd && patch.originEndOffset() > editStart) {
        return MarkerTransformResult(startOffset, endOffset + newLength)
      }
      return MarkerTransformResult(startOffset + delta, endOffset + delta)
    }
    if (startOffset <= editStart && endOffset >= editEnd) {
      return MarkerTransformResult(startOffset, endOffset + delta)
    }
    if (startOffset >= editStart && startOffset <= editEnd && endOffset > editEnd) {
      return MarkerTransformResult(editStart + newLength, endOffset + delta)
    }
    if (endOffset <= editEnd && startOffset < editStart) {
      return MarkerTransformResult(startOffset, editStart)
    }
    return MarkerTransformResult(MarkerTransformError.INVALIDATED_BY_EDIT)
  }
}
