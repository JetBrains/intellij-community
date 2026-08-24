// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import org.jetbrains.annotations.ApiStatus

/**
 * Transforms one marker in response to a logical document edit.
 *
 * Implementations must preserve the marker ID. Expensive data shared by several markers should be prepared by the
 * marker root and supplied through a future edit context rather than recomputed for every marker.
 */
@ApiStatus.Internal
fun interface MarkerPolicy {
  fun transform(entry: MarkerEntry, edit: MarkerEdit): MarkerTransformResult
}

/**
 * Logical edit coordinates used by marker transformation policies.
 */
@ApiStatus.Internal
data class MarkerEdit(
  val startOffset: Int,
  val endOffset: Int,
  val newLength: Int,
  val originStartOffset: Int,
  val originEndOffset: Int,
  val moveOffset: Int,
)

/**
 * Final state of one marker after an edit.
 */
@ApiStatus.Internal
sealed interface MarkerTransformResult {
  data class Valid(val entry: MarkerEntry) : MarkerTransformResult
  data class Invalid(val reason: String) : MarkerTransformResult
}

/**
 * Standard IntelliJ range-marker transformation policy.
 */
@ApiStatus.Internal
object DefaultMarkerPolicy : MarkerPolicy {
  override fun transform(entry: MarkerEntry, edit: MarkerEdit): MarkerTransformResult {
    return if (entry.startOffset == entry.endOffset) {
      transformPoint(entry, edit)
    }
    else {
      transformRange(entry, edit)
    }
  }

  private fun transformPoint(entry: MarkerEntry, edit: MarkerEdit): MarkerTransformResult {
    val point = entry.startOffset
    val editStart = edit.startOffset
    val editEnd = edit.endOffset
    val oldLength = editEnd - editStart
    val newLength = edit.newLength

    if (editStart < point && point < editEnd) return MarkerTransformResult.Invalid(INVALIDATED_BY_EDIT)

    if (oldLength == 0 && editStart == point && entry.spec.isGreedyToRight) {
      return MarkerTransformResult.Valid(entry.copy(endOffset = point + newLength))
    }

    if (oldLength == 0 && editStart == point && entry.spec.isStickingToRight) {
      val shifted = point + newLength
      return MarkerTransformResult.Valid(entry.copy(startOffset = shifted, endOffset = shifted))
    }

    if (point > editEnd || point == editEnd && oldLength > 0) {
      val shifted = point + newLength - oldLength
      return MarkerTransformResult.Valid(entry.copy(startOffset = shifted, endOffset = shifted))
    }

    return MarkerTransformResult.Valid(entry)
  }

  private fun transformRange(entry: MarkerEntry, edit: MarkerEdit): MarkerTransformResult {
    val startOffset = entry.startOffset
    val endOffset = entry.endOffset
    val editStart = edit.startOffset
    val editEnd = edit.endOffset
    val newLength = edit.newLength
    val delta = newLength - (editEnd - editStart)

    if (editStart > endOffset) return MarkerTransformResult.Valid(entry)
    if (!entry.spec.isGreedyToRight && endOffset == editStart) {
      if (editStart == editEnd && edit.originStartOffset < editStart) {
        return MarkerTransformResult.Valid(entry.copy(endOffset = endOffset + newLength))
      }
      return MarkerTransformResult.Valid(entry)
    }
    if (startOffset > editEnd) {
      return MarkerTransformResult.Valid(
        entry.copy(startOffset = startOffset + delta, endOffset = endOffset + delta)
      )
    }
    if (!entry.spec.isGreedyToLeft && startOffset == editEnd) {
      if (editStart == editEnd && edit.originEndOffset > editStart) {
        return MarkerTransformResult.Valid(entry.copy(endOffset = endOffset + newLength))
      }
      return MarkerTransformResult.Valid(
        entry.copy(startOffset = startOffset + delta, endOffset = endOffset + delta)
      )
    }
    if (startOffset <= editStart && endOffset >= editEnd) {
      return MarkerTransformResult.Valid(entry.copy(endOffset = endOffset + delta))
    }
    if (startOffset >= editStart && startOffset <= editEnd && endOffset > editEnd) {
      return MarkerTransformResult.Valid(
        entry.copy(startOffset = editStart + newLength, endOffset = endOffset + delta)
      )
    }
    if (endOffset <= editEnd && startOffset < editStart) {
      return MarkerTransformResult.Valid(entry.copy(endOffset = editStart))
    }
    return MarkerTransformResult.Invalid(INVALIDATED_BY_EDIT)
  }

  private const val INVALIDATED_BY_EDIT = "Marker was invalidated by a document edit"
}
