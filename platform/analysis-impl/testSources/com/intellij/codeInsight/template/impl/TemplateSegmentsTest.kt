// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.RangeMarkersImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.Processor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class TemplateSegmentsTest {
  @Test
  fun `matching range reuses the marker and makes both boundaries greedy`() {
    withSegment { document, segments ->
      val marker = document.singleMarker()

      segments.replaceSegmentAt(0, 2, 4)

      assertSame(marker, document.singleMarker())
      document.insertString(4, "R")
      document.insertString(2, "L")
      assertEquals(2, segments.getSegmentStart(0))
      assertEquals(6, segments.getSegmentEnd(0))
    }
  }

  @Test
  fun `matching range preserves asymmetric greediness when requested`() {
    withSegment { document, segments ->
      val marker = document.singleMarker()
      marker.isGreedyToLeft = true

      segments.replaceSegmentAt(0, 2, 4, true)

      assertSame(marker, document.singleMarker())
      document.insertString(4, "R")
      document.insertString(2, "L")
      assertEquals(2, segments.getSegmentStart(0))
      assertEquals(5, segments.getSegmentEnd(0))
    }
  }

  @Test
  fun `changed range replaces the marker`() {
    withSegment { document, segments ->
      val marker = document.singleMarker()

      segments.replaceSegmentAt(0, 1, 5)

      assertNotSame(marker, document.singleMarker())
      assertFalse(marker.isValid)
      assertEquals(1, segments.getSegmentStart(0))
      assertEquals(5, segments.getSegmentEnd(0))
    }
  }

  @Test
  fun `invalid marker is replaced for the same requested range`() {
    withSegment { document, segments ->
      val marker = document.singleMarker()
      marker.dispose()

      segments.replaceSegmentAt(0, 2, 4, true)

      assertNotSame(marker, document.singleMarker())
      assertTrue(segments.isValid(0))
      assertEquals(2, segments.getSegmentStart(0))
      assertEquals(4, segments.getSegmentEnd(0))
    }
  }

  @Test
  @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
  fun `overlap correction handles unordered ranges and ignores unrelated markers`() {
    for (wrapDocument in listOf(false, true)) {
      withSegments(
        0 to 3, 3 to 5, 2 to 2, 2 to 4, 3 to 6, 2 to 2, 1 to 3, 4 to 4, 0 to 1,
        documentWrapper = { document -> if (wrapDocument) object : Document by document {} else document },
      ) { document, segments ->
        val unrelated = document.createRangeMarker(3, 3)
        try {
          segments.fixOverlappedSegments(3)

          assertEquals(listOf(0 to 2, 3 to 5, 2 to 2, 2 to 4, 4 to 6, 4 to 4, 1 to 3, 4 to 4, 0 to 1), segments.ranges())
          assertTrue(unrelated.isValid)
          assertEquals(3, unrelated.startOffset)
          assertEquals(3, unrelated.endOffset)
        }
        finally {
          unrelated.dispose()
        }
      }
    }
  }

  @Test
  @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
  fun `overlap correction supports DocumentEx decorators and incomplete queries`() {
    for (queryCompletes in listOf(false, true)) {
      var queries = 0
      withSegments(0 to 3, 2 to 4, 3 to 6, documentWrapper = { document ->
        object : DocumentEx by document {
          override fun processRangeMarkersOverlappingWith(start: Int, end: Int, processor: Processor<in RangeMarker>): Boolean {
            queries++
            return queryCompletes && document.processRangeMarkersOverlappingWith(start, end, processor)
          }
        }
      }) { _, segments ->
        val previousQueries = queries
        segments.fixOverlappedSegments(1)

        assertTrue(queries > previousQueries)
        assertEquals(listOf(0 to 2, 2 to 4, 4 to 6), segments.ranges())
      }
    }
  }

  @Test
  fun `overlap correction ignores segments from another template`() {
    withSegments(0 to 3, 2 to 4, 3 to 6) { document, segments ->
      val otherSegments = TemplateSegments(document)
      repeat(4) { otherSegments.addSegment(3, 3) }
      try {
        segments.fixOverlappedSegments(1)

        assertEquals(listOf(0 to 2, 2 to 4, 4 to 6), segments.ranges())
        assertEquals(List(4) { 3 to 3 }, otherSegments.ranges())
      }
      finally {
        otherSegments.removeAll()
      }
    }
  }

  @Test
  fun `overlap correction tracks replaced and newly added markers`() {
    withSegments(0 to 3, 2 to 4, 3 to 6) { document, segments ->
      segments.replaceSegmentAt(0, 1, 3)
      document.markers().single { it.startOffset == 3 && it.endOffset == 6 }.dispose()
      segments.replaceSegmentAt(2, 3, 6, true)
      segments.addSegment(2, 3)

      segments.fixOverlappedSegments(1)

      assertEquals(listOf(1 to 2, 2 to 4, 4 to 6, 4 to 4), segments.ranges())
    }
  }

  @Test
  fun `overlap correction preserves greediness when it trims a segment`() {
    withSegments(0 to 3, 2 to 4, 3 to 6) { document, segments ->
      document.markers().single { it.startOffset == 0 }.isGreedyToLeft = true
      document.markers().single { it.endOffset == 6 }.isGreedyToRight = true

      segments.fixOverlappedSegments(1)

      val left = document.markers().single { it.startOffset == 0 }
      assertTrue(left.isGreedyToLeft)
      assertFalse(left.isGreedyToRight)
      val right = document.markers().single { it.endOffset == 6 }
      assertFalse(right.isGreedyToLeft)
      assertTrue(right.isGreedyToRight)
    }
  }

  @Test
  fun `an empty current segment leaves overlapping segments unchanged`() {
    withSegments(1 to 3, 2 to 2, 2 to 4) { _, segments ->
      segments.fixOverlappedSegments(-1)
      segments.fixOverlappedSegments(1)

      assertEquals(listOf(1 to 3, 2 to 2, 2 to 4), segments.ranges())
    }
  }

  @Test
  fun `overlap correction can restore a marker invalidated by a replacement`() {
    withSegments(1 to 3, 0 to 4, 3 to 5) { document, segments ->
      document.replaceString(0, 4, "ABCD")
      assertFalse(segments.isValid(0))

      segments.fixOverlappedSegments(1, true)

      assertTrue(segments.isValid(0))
      assertEquals(listOf(0 to 0, 0 to 4, 4 to 5), segments.ranges())
    }
  }

  private fun withSegment(action: (DocumentImpl, TemplateSegments) -> Unit): Unit = withSegments(2 to 4, action = action)

  private fun withSegments(
    vararg ranges: Pair<Int, Int>,
    documentWrapper: (DocumentImpl) -> Document = { it },
    action: (DocumentImpl, TemplateSegments) -> Unit,
  ) {
    for (useSnapshotMarkers in listOf(false, true)) {
      RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException>(useSnapshotMarkers) {
        val document = DocumentImpl("abcdef", true)
        val segments = TemplateSegments(documentWrapper(document))
        for ((start, end) in ranges) {
          segments.addSegment(start, end)
        }
        try {
          action(document, segments)
        }
        finally {
          segments.removeAll()
        }
      }
    }
  }

  private fun TemplateSegments.ranges(): List<Pair<Int, Int>> = (0 until segmentsCount).map {
    getSegmentStart(it) to getSegmentEnd(it)
  }

  private fun DocumentImpl.singleMarker(): RangeMarker = markers().single()

  private fun DocumentImpl.markers(): List<RangeMarker> {
    val markers = ArrayList<RangeMarker>()
    processRangeMarkers {
      markers.add(it)
      true
    }
    return markers
  }
}
