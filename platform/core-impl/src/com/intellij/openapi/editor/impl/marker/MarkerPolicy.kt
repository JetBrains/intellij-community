// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import org.jetbrains.annotations.ApiStatus

/**
 * Transforms one marker in response to a logical document edit.
 *
 * Implementations must preserve the marker ID.
 */
@ApiStatus.Internal
fun interface MarkerPolicy {
  /** True when this policy uses persistent line translation for large document replacements. */
  val isPersistent: Boolean
    get() = false

  fun transform(
    entry: MarkerEntry,
    patch: DocumentTextPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
  ): MarkerTransformResult
}

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
  override fun transform(
    entry: MarkerEntry,
    patch: DocumentTextPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
  ): MarkerTransformResult {
    return if (entry.startOffset == entry.endOffset) {
      transformPoint(entry, patch)
    }
    else {
      transformRange(entry, patch)
    }
  }

  private fun transformPoint(entry: MarkerEntry, patch: DocumentTextPatch): MarkerTransformResult {
    val point = entry.startOffset
    val editStart = patch.startOffset()
    val editEnd = patch.endOffset()
    val oldLength = editEnd - editStart
    val newLength = patch.newFragment().length

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

  private fun transformRange(entry: MarkerEntry, patch: DocumentTextPatch): MarkerTransformResult {
    val startOffset = entry.startOffset
    val endOffset = entry.endOffset
    val editStart = patch.startOffset()
    val editEnd = patch.endOffset()
    val newLength = patch.newFragment().length
    val delta = newLength - (editEnd - editStart)

    if (editStart > endOffset) return MarkerTransformResult.Valid(entry)
    if (!entry.spec.isGreedyToRight && endOffset == editStart) {
      if (editStart == editEnd && patch.originStartOffset() < editStart) {
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
      if (editStart == editEnd && patch.originEndOffset() > editStart) {
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
