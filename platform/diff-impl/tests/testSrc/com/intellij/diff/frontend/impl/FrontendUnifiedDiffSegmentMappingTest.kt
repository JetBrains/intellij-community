// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import com.intellij.diff.tools.fragmented.LineNumberConvertor
import junit.framework.TestCase

internal class FrontendUnifiedDiffSegmentMappingTest : TestCase() {
  fun testUnchangedContent() {
    assertEquivalent(
      Segment(0, 0, 8, 8),
    )
  }

  fun testInsertedDeletedAndModifiedBlocks() {
    assertEquivalent(
      Segment(0, 0, 3, 3),
      Segment(3, 3, 2, 0),
      Segment(5, 3, 3, 3),
      Segment(8, 6, 2, 4),
      Segment(10, 10, 3, 3),
    )
  }

  fun testEmptySide() {
    assertEquivalent(
      Segment(0, 0, 5, 0),
    )
  }

  fun testEmptyContent() {
    assertEquivalent()
  }

  fun testFinalLineWithoutTrailingNewline() {
    assertEquivalent(
      Segment(0, 0, 1, 1),
    )
  }

  private fun assertEquivalent(vararg segments: Segment) {
    val builder = LineNumberConvertor.Builder()
    for ((unifiedStart, sideStart, unifiedLength, sideLength) in segments) {
      builder.put(unifiedStart, sideStart, unifiedLength, sideLength)
    }
    val convertor = builder.build()
    val mapping = FrontendUnifiedDiffSegmentMapping(convertor.ranges)

    for (line in -2..20) {
      assertEquals("strict unified line $line", convertor.convert(line), mapping.unifiedToSide(line, strict = true))
      assertEquals("approximate unified line $line", convertor.convertApproximate(line), mapping.unifiedToSide(line, strict = false))
      assertEquals("strict side line $line", convertor.convertInv(line), mapping.sideToUnified(line, strict = true))
      assertEquals("approximate side line $line", convertor.convertApproximateInv(line), mapping.sideToUnified(line, strict = false))
    }
  }

  private data class Segment(
    val unifiedStart: Int,
    val sideStart: Int,
    val unifiedLength: Int,
    val sideLength: Int,
  )
}
