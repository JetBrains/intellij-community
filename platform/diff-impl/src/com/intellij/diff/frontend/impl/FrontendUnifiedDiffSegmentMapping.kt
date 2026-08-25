// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import com.intellij.diff.util.Range
import org.jetbrains.annotations.ApiStatus
import kotlin.math.min

/**
 * Converts lines between the unified document of a unified diff and one of its sides, the way
 * `com.intellij.diff.tools.fragmented.LineNumberConvertor` does.
 *
 * The conversion is fully determined by the segments the convertor was built from, so it can be replayed away from the viewer
 * that computed the diff - which is what the split-mode frontend does with the segments the backend sends it.
 *
 * @param segments the segments of one side, as `com.intellij.diff.tools.fragmented.UnifiedDiffViewer.getLineNumberMapping`
 *                 returns them: [Range.start1]/[Range.end1] in the unified document and [Range.start2]/[Range.end2] in the side
 */
@ApiStatus.Internal
class FrontendUnifiedDiffSegmentMapping(segments: List<Range>) {
  private val fromUnified = segments.sortedBy { it.start1 }

  /**
   * The same segments flipped, so that [convert] only ever has to run from side 1 to side 2. Segments starting on the same
   * line of the side collapse to the last of them, matching the inverted `TreeMap` of `LineNumberConvertor`.
   */
  private val fromSide = segments.associateBy { it.start2 }.values.map { it.flip() }.sortedBy { it.start1 }

  fun unifiedToSide(line: Int, strict: Boolean): Int = convert(fromUnified, line, strict)

  fun sideToUnified(line: Int, strict: Boolean): Int = convert(fromSide, line, strict)

  private fun convert(segments: List<Range>, line: Int, strict: Boolean): Int {
    val index = segments.binarySearch { segment ->
      if (segment.start1 <= line) -1 else 1
    }.let { insertionPoint ->
      if (insertionPoint >= 0) insertionPoint else -insertionPoint - 2
    }
    if (index < 0) return if (strict) -1 else 0

    val segment = segments[index]
    val shift = segment.start2 - segment.start1
    if (strict) {
      if (line >= segment.end1 || segment.end1 - segment.start1 != segment.end2 - segment.start2) return -1
      return shift + line
    }
    return min(shift + line, segment.end2)
  }
}

private fun Range.flip(): Range = Range(start2, end2, start1, end1)
