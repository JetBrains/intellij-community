// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import org.jetbrains.annotations.ApiStatus
import kotlin.math.min

@ApiStatus.Internal
data class FrontendUnifiedDiffMappingSegment(
  val unifiedStart: Int,
  val sideStart: Int,
  val unifiedLength: Int,
  val sideLength: Int,
)

@ApiStatus.Internal
class FrontendUnifiedDiffSegmentMapping(segments: List<FrontendUnifiedDiffMappingSegment>) {
  private val unifiedSegments = segments.sortedBy { it.unifiedStart }
  // Match LineNumberConvertor's inverted TreeMap: for equal side starts, the last segment wins.
  private val sideSegments = segments.associateBy { it.sideStart }.values.sortedBy { it.sideStart }

  fun unifiedToSide(line: Int, strict: Boolean): Int = convert(unifiedSegments, line, strict, fromUnified = true)

  fun sideToUnified(line: Int, strict: Boolean): Int = convert(sideSegments, line, strict, fromUnified = false)

  private fun convert(
    segments: List<FrontendUnifiedDiffMappingSegment>,
    line: Int,
    strict: Boolean,
    fromUnified: Boolean,
  ): Int {
    val index = segments.binarySearch { segment ->
      val start = if (fromUnified) segment.unifiedStart else segment.sideStart
      if (start <= line) -1 else 1
    }.let { insertionPoint ->
      if (insertionPoint >= 0) insertionPoint else -insertionPoint - 2
    }
    if (index < 0) return if (strict) -1 else 0

    val segment = segments[index]
    val start = if (fromUnified) segment.unifiedStart else segment.sideStart
    val length = if (fromUnified) segment.unifiedLength else segment.sideLength
    val otherStart = if (fromUnified) segment.sideStart else segment.unifiedStart
    val otherLength = if (fromUnified) segment.sideLength else segment.unifiedLength
    if (strict) {
      if (line >= start + length || length != otherLength) return -1
      return otherStart - start + line
    }
    return min(otherStart - start + line, otherStart + otherLength)
  }
}
