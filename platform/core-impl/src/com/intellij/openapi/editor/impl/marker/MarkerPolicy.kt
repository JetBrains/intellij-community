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

  /**
   * Transforms [entry] for [patch]. The root applies all pending ancestor shifts before this call.
   * [MarkerEntry.nodeStart] and [MarkerEntry.nodeEnd] therefore use [beforeText] coordinates.
   */
  fun transform(
    entry: MarkerEntry,
    patch: DocumentTextPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
  ): MarkerTransformResult

  /** Applies policy rules after a text move retargets [entry]. */
  fun afterRetarget(entry: MarkerEntry, text: DocumentText): MarkerTransformResult = MarkerTransformResult(entry)
}

/**
 * Final state of one marker after an edit.
 * [errorReason] is null when the marker stays valid.
 */
@ApiStatus.Internal
data class MarkerTransformResult(
  val entry: MarkerEntry,
  val errorReason: String? = null,
)

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
    return if (entry.nodeStart == entry.nodeEnd) {
      transformPoint(entry, patch)
    }
    else {
      transformRange(entry, patch)
    }
  }

  private fun transformPoint(entry: MarkerEntry, patch: DocumentTextPatch): MarkerTransformResult {
    val point = entry.nodeStart
    val editStart = patch.startOffset()
    val editEnd = patch.endOffset()
    val oldLength = editEnd - editStart
    val newLength = patch.newFragment().length

    if (editStart < point && point < editEnd) return MarkerTransformResult(entry, INVALIDATED_BY_EDIT)

    if (oldLength == 0 && editStart == point && entry.spec.isGreedyToRight) {
      return MarkerTransformResult(entry.copy(nodeEnd = point + newLength))
    }

    if (oldLength == 0 && editStart == point && entry.spec.isStickingToRight) {
      val shifted = point + newLength
      return MarkerTransformResult(entry.copy(nodeStart = shifted, nodeEnd = shifted))
    }

    if (point > editEnd || point == editEnd && oldLength > 0) {
      val shifted = point + newLength - oldLength
      return MarkerTransformResult(entry.copy(nodeStart = shifted, nodeEnd = shifted))
    }

    return MarkerTransformResult(entry)
  }

  private fun transformRange(entry: MarkerEntry, patch: DocumentTextPatch): MarkerTransformResult {
    val startOffset = entry.nodeStart
    val endOffset = entry.nodeEnd
    val editStart = patch.startOffset()
    val editEnd = patch.endOffset()
    val newLength = patch.newFragment().length
    val delta = newLength - (editEnd - editStart)

    if (editStart > endOffset) return MarkerTransformResult(entry)
    if (!entry.spec.isGreedyToRight && endOffset == editStart) {
      if (editStart == editEnd && patch.originStartOffset() < editStart) {
        return MarkerTransformResult(entry.copy(nodeEnd = endOffset + newLength))
      }
      return MarkerTransformResult(entry)
    }
    if (startOffset > editEnd) {
      return MarkerTransformResult(
        entry.copy(nodeStart = startOffset + delta, nodeEnd = endOffset + delta)
      )
    }
    if (!entry.spec.isGreedyToLeft && startOffset == editEnd) {
      if (editStart == editEnd && patch.originEndOffset() > editStart) {
        return MarkerTransformResult(entry.copy(nodeEnd = endOffset + newLength))
      }
      return MarkerTransformResult(
        entry.copy(nodeStart = startOffset + delta, nodeEnd = endOffset + delta)
      )
    }
    if (startOffset <= editStart && endOffset >= editEnd) {
      return MarkerTransformResult(entry.copy(nodeEnd = endOffset + delta))
    }
    if (startOffset >= editStart && startOffset <= editEnd && endOffset > editEnd) {
      return MarkerTransformResult(
        entry.copy(nodeStart = editStart + newLength, nodeEnd = endOffset + delta)
      )
    }
    if (endOffset <= editEnd && startOffset < editStart) {
      return MarkerTransformResult(entry.copy(nodeEnd = editStart))
    }
    return MarkerTransformResult(entry, INVALIDATED_BY_EDIT)
  }

  private const val INVALIDATED_BY_EDIT: String = "Marker was invalidated by a document edit"
}
