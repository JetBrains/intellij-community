// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.RangeMarkersImpl
import com.intellij.testFramework.junit5.TestApplication
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

  private fun withSegment(action: (DocumentImpl, TemplateSegments) -> Unit) {
    for (useSnapshotMarkers in listOf(false, true)) {
      RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException>(useSnapshotMarkers) {
        val document = DocumentImpl("abcdef", true)
        val segments = TemplateSegments(document)
        segments.addSegment(2, 4)
        try {
          action(document, segments)
        }
        finally {
          segments.removeAll()
        }
      }
    }
  }

  private fun DocumentImpl.singleMarker(): RangeMarker {
    val markers = ArrayList<RangeMarker>()
    processRangeMarkers {
      markers.add(it)
      true
    }
    return markers.single()
  }
}
