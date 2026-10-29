// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentCore
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.RangeMarkersImpl
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.util.ObjectUtilsRt
import com.intellij.util.ref.GCUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class SnapshotRangeMarkerImplTest {
  @Test
  fun `marker getters follow the snapshot during publication`() {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val core = DocumentImpl("abcdefgh", true).core
      var current = core.snapshot()
      val document = DocumentImpl(object : DocumentCore by core {
        override fun snapshot(): DocumentBranch = current
      }, null)
      val marker = document.createRangeMarker(2, 4)
      assertEquals(TextRange(2, 4), marker.textRange)
      val before = current
      val patch = DocumentPatch.simple(0, 0, "xx", newModStamp = 1, clearLineFlags = false)
      var after = before
      for (op in patch.ops()) after = after.applyOp(op)

      assertTrue(document.snapshotMarkerStores.tryCommit(before, after, patch, isCurrent = { current === before }, publish = {
        assertEquals(TextRange(2, 4), marker.textRange)
        current = after
        assertEquals(TextRange(4, 6), marker.textRange)
      }))

      assertEquals(TextRange(4, 6), marker.textRange)
    }
  }

  @Test
  fun `a failed snapshot publication preserves marker offsets`() {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val document = DocumentImpl("abcdefgh", true)
      val marker = document.createRangeMarker(2, 4)
      assertEquals(TextRange(2, 4), marker.textRange)
      val before = document.core.snapshot()
      val patch = DocumentPatch.simple(0, 0, "xx", newModStamp = 1, clearLineFlags = false)
      var after = before
      for (op in patch.ops()) after = after.applyOp(op)

      assertThrows(IllegalStateException::class.java) {
        document.snapshotMarkerStores.tryCommit(before, after, patch, isCurrent = { true }, publish = { error("Publication failed") })
      }

      assertSame(before, document.core.snapshot())
      assertEquals(TextRange(2, 4), marker.textRange)
    }
  }

  @Test
  fun `current root cache releases earlier snapshots`() {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val document = DocumentImpl("abcdefgh", true)
      val marker = document.createRangeMarker(2, 4)
      assertEquals(2, marker.startOffset)
      val previous = WeakReference(document.core.snapshot())

      document.insertString(0, "xx")
      GCUtil.tryGcSoftlyReachableObjects { previous.get() == null }

      assertNull(previous.get())
      assertEquals(4, marker.startOffset)
      ObjectUtilsRt.reachabilityFence(marker)
    }
  }

  @Test
  fun `a retained store does not retain the current snapshot`() {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val (store, snapshot) = createStoreWithWeakSnapshot()

      GCUtil.tryGcSoftlyReachableObjects { snapshot.get() == null }

      assertNull(snapshot.get())
      ObjectUtilsRt.reachabilityFence(store)
    }
  }

  private fun createStoreWithWeakSnapshot(): Pair<SnapshotMarkerRootStore<*>, WeakReference<com.intellij.openapi.editor.ex.DocumentSnapshot>> {
    val document = DocumentImpl("abcdefgh", true)
    val marker = document.createRangeMarker(2, 4)
    assertEquals(2, marker.startOffset)
    return document.rangeMarkers.rootStore() to WeakReference(document.core.snapshot())
  }

  @Test
  fun `validity checks do not resolve offsets`() {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val document = DocumentImpl("abcdefgh", true)
      val marker = document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
      val point = document.createRangeMarker(3, 3)
      val reference = marker.currentRootReference()
      val original = reference.get()
      val removed = original.remove(marker.markerHandle)
      for (root in listOf(original, removed, original)) {
        reference.set(object : PMarkerRoot by root {
          override fun resolve(handle: SnapshotMarkerReference<*>): Long = error("Validity checks must not resolve offsets")
        })

        assertEquals(root === original, marker.isValid)
        assertTrue(point.isValid)
      }

      reference.set(original)
      marker.dispose()
      assertFalse(marker.isValid)
      assertTrue(point.isValid)
    }
  }

  @Test
  fun `root insertion does not need a live marker for its policy`() {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
      val marker = SnapshotMarkerEngineImpl.createRangeMarker(DocumentImpl("", true), 0, 0, spec, false, DefaultMarkerPolicy)
      val handle = PMarkerRootImpl.empty().createHandle(marker as SnapshotRangeMarkerImpl, false)
      (handle as Reference<*>).clear()
      val policy = MarkerPolicy { _, _, _, _ -> error("Insertion must not transform the marker") }
      val root = PMarkerRootImpl.empty().insert(handle, 2, 4, spec, flavorFlags = 0, policy = policy)

      assertEquals(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(2, 4)), root.resolve(handle))
      assertSame(policy, root.overlappingIterator(2, 4, 0).next().policy)
    }
  }

  @Test
  fun `policy survives changes to marker flags`() {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val document = DocumentImpl("abcdefgh", true)
      val policy = MarkerPolicy { entry, _, _, _ -> MarkerTransformResult(entry.nodeStart, 6) }
      val marker = SnapshotMarkerEngineImpl.createRangeMarker(
        document, 2, 4, MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false), policy = policy,
      ) as SnapshotRangeMarkerImpl

      marker.isGreedyToLeft = true
      marker.isGreedyToRight = true
      marker.setStickingToRight(true)

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
      assertEquals(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(2, 4)), marker.fallbackRange)
      val reference = marker.currentRootReference()
      val before = document.core.snapshot().text()
      val patch = DocumentPatch.simple(0, 0, "xx", newModStamp = 1, clearLineFlags = false)
      val shifted = reference.get().applyPatch(patch, before, before.replace(0, 0, "xx"))
      reference.set(shifted)
      assertEquals(TextRange(4, 6), marker.textRange)

      reference.set(shifted.remove(marker.markerHandle))

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
      repeat(2) {
        assertEquals(PMarkerResolution.ABSENT, SnapshotMarkerEngineImpl.resolveRangeMarker(marker, reference.get()))
        assertFalse(marker.isValid)
        assertEquals(4, marker.startOffset)
        assertEquals(6, marker.endOffset)
        assertEquals(TextRange(4, 6), marker.textRange)
      }
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
      val resolution = root.resolve(marker.markerHandle)
      val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
      val otherRoot = if (absent) root.remove(marker.markerHandle)
      else PMarkerRootImpl.empty().insert(marker.markerHandle, 4, 6, spec, flavorFlags = 0)
      val otherResolution = otherRoot.resolve(marker.markerHandle)
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
