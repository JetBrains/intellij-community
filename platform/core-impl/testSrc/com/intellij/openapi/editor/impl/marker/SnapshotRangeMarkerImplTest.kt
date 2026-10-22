// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.RangeMarkersImpl
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRangeScalarUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class SnapshotRangeMarkerImplTest {
  @Test
  fun `validity checks do not resolve offsets`() {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val document = DocumentImpl("abcdefgh", true)
      val marker = document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
      val point = document.createRangeMarker(3, 3)
      val reference = marker.currentRootReference()
      val original = reference.get()
      val removed = original.remove(marker.id)
      for (root in listOf(original, removed, original)) {
        reference.set(object : PMarkerRoot by root {
          override fun resolve(markerId: Long): Long = error("Validity checks must not resolve offsets")
        })

        assertEquals(root === original, marker.isValid)
        assertTrue(point.isValid)
      }

      marker.dispose()
      assertFalse(marker.isValid)
      assertTrue(point.isValid)
    }
  }

  @Test
  fun `root insertion does not resolve the marker reference for its policy`() {
    val reference = object : SnapshotMarkerReference {
      override fun get(): SnapshotRangeMarkerImpl = error("Insertion must not resolve the marker reference")
    }
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val root = PMarkerRootImpl.empty().insert(1, 2, 4, spec, flavorFlags = 0, markerReference = reference)

    assertEquals(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(2, 4)), root.resolve(1))
  }

  @Test
  fun `policy survives changes to marker flags`() {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val document = DocumentImpl("abcdefgh", true)
      val policy = MarkerPolicy { entry, _, _, _ -> MarkerTransformResult(entry.copy(nodeEnd = 6)) }
      val marker = SnapshotMarkerEngineImpl.createRangeMarker(
        document, document.core.snapshot(), 2, 4, MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false), policy = policy,
      ) as SnapshotRangeMarkerImpl

      marker.isGreedyToLeft = true
      marker.isGreedyToRight = true
      marker.setStickingToRight(true)

      assertSame(policy, marker.policy)
      marker.currentRootReference().get().processAllRangeMarkers { entry, _ ->
        assertSame(policy, entry.policy)
        assertEquals(MarkerSpec.create(isGreedyToLeft = true, isGreedyToRight = true, isStickingToRight = true), entry.spec)
        true
      }
      document.insertString(3, "x")
      assertTrue(marker.isValid)
      assertEquals(TextRange(2, 6), marker.textRange)
    }
  }

  @Test
  fun `absent marker getters use creation offsets instead of cached offsets`() {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val document = DocumentImpl("abcdefgh", true)
      val marker = document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
      assertEquals(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(2, 4)), marker.initialRange)
      val reference = marker.currentRootReference()
      val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
      val shifted = reference.get().remove(marker.id).insert(marker.id, 4, 6, spec, flavorFlags = 0)
      reference.set(shifted)
      assertEquals(TextRange(4, 6), marker.textRange)

      reference.set(shifted.remove(marker.id))

      repeat(2) {
        assertEquals(PMarkerResolution.ABSENT, SnapshotMarkerEngineImpl.resolveRangeMarker(marker, reference.get()))
        assertFalse(marker.isValid)
        assertEquals(2, marker.startOffset)
        assertEquals(4, marker.endOffset)
        assertEquals(TextRange(2, 4), marker.textRange)
      }
      reference.set(shifted)
      assertTrue(marker.isValid)
      assertEquals(TextRange(4, 6), marker.textRange)

      marker.dispose()
      assertFalse(marker.isValid)
      assertEquals(TextRange(2, 4), marker.textRange)
      assertEquals(PMarkerResolution.invalid(TextRangeScalarUtil.toScalarRange(2, 4)), SnapshotMarkerEngineImpl.resolveRangeMarker(marker, reference.get()))
    }
  }

  @Test
  fun `concurrent cache updates keep offsets paired with their roots`() {
    checkConcurrentCacheUpdates(absent = false)
  }

  @Test
  fun `concurrent cache updates keep states paired with their roots`() {
    checkConcurrentCacheUpdates(absent = true)
  }

  private fun checkConcurrentCacheUpdates(absent: Boolean) {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val document = DocumentImpl("abcdefgh", true)
      val marker = document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
      val root = marker.currentRootReference().get()
      val resolution = root.resolve(marker.markerId)
      val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
      val otherRoot = if (absent) root.remove(marker.markerId)
      else PMarkerRootImpl.empty().insert(marker.markerId, 4, 6, spec, flavorFlags = 0)
      val otherResolution = otherRoot.resolve(marker.markerId)
      val start = CountDownLatch(1)
      val executor = Executors.newFixedThreadPool(3)
      try {
        val writers = listOf(root to resolution, otherRoot to otherResolution).map { (cachedRoot, cachedResolution) ->
          executor.submit {
            assertTrue(start.await(10, TimeUnit.SECONDS))
            repeat(100_000) {
              marker.cacheResolution(cachedRoot, cachedResolution)
            }
          }
        }
        val reader = executor.submit {
          assertTrue(start.await(10, TimeUnit.SECONDS))
          repeat(100_000) {
            assertEquals(TextRange(2, 4), marker.textRange)
            assertTrue(marker.isValid)
          }
        }
        start.countDown()
        writers.forEach { it.get(30, TimeUnit.SECONDS) }
        reader.get(30, TimeUnit.SECONDS)
        assertEquals(TextRange(2, 4), marker.textRange)
      }
      finally {
        executor.shutdownNow()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
      }
    }
  }
}
