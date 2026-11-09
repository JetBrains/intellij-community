// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentCore
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.testFramework.junit5.UsePMarkerImplementation
import com.intellij.util.ObjectUtilsRt
import com.intellij.util.ref.GCUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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

@UsePMarkerImplementation
internal class SnapshotRangeMarkerImplTest {
  @Test
  fun `disposal keeps current offsets when the cached start belongs to another marker`() {
    val document = DocumentImpl("abcdefgh", true)
    val marker = document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
    val reference = marker.currentRootReference()
    val original = reference.get()
    val before = document.core.snapshot().text()
    val patch = DocumentPatch.simple(0, 0, "xx", newModStamp = 1, clearLineFlags = false)
    val shifted = original.applyPatch(patch, before, before.replace(0, 0, "xx"))
    reference.set(shifted)
    val other = document.createRangeMarker(2, 3) as SnapshotRangeMarkerImpl

    marker.dispose()

    assertFalse(marker.isValid)
    assertEquals(TextRange(4, 6), marker.textRange)
    assertTrue(other.isValid)
    assertEquals(TextRange(2, 3), other.textRange)
    assertEquals(TextRangeScalarUtil.toScalarRange(2, 4), original.resolve(marker.markerHandle))
    assertEquals(TextRangeScalarUtil.toScalarRange(4, 6), shifted.resolve(marker.markerHandle))
  }

  @Test
  fun `disposal retains offsets without allocating a resolution map`() {
    val document = DocumentImpl("abcdefgh", true)
    val markers = List(12) { document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl }
    for (marker in markers) {
      val root = marker.currentRootReference().get()

      marker.dispose()

      assertFalse(marker.isValid)
      assertEquals(TextRange(2, 4), marker.textRange)
      repeat(2) {
        assertEquals(TextRangeScalarUtil.toScalarRange(2, 4), root.resolve(marker.markerHandle))
        assertTrue(root.isValid(marker.markerHandle))
        assertNull(resolutionMap(root))
      }
      assertEquals(PMarkerResolution.ABSENT, marker.currentRootReference().get().resolve(marker.markerHandle))
    }
  }

  @Test
  fun `metadata changes share the resolution map allocated for a second marker`() {
    val document = DocumentImpl("abcdefgh", true)
    val first = document.createRangeMarker(1, 3) as SnapshotRangeMarkerImpl
    val second = document.createRangeMarker(5, 7) as SnapshotRangeMarkerImpl
    val root = first.currentRootReference().get()
    val firstRange = TextRangeScalarUtil.toScalarRange(1, 3)
    val secondRange = TextRangeScalarUtil.toScalarRange(5, 7)
    assertEquals(firstRange, root.resolve(first.markerHandle))
    assertNull(resolutionMap(root))

    val updated = root.updateSpec(first.markerHandle, MarkerSpec.create(isGreedyToLeft = true, isGreedyToRight = false))
    assertNull(resolutionMap(updated))
    assertEquals(secondRange, updated.resolve(second.markerHandle))

    assertNotNull(resolutionMap(root))
    assertSame(resolutionMap(root), resolutionMap(updated))
    for (current in listOf(root, updated)) {
      assertEquals(firstRange, current.resolve(first.markerHandle))
      assertEquals(secondRange, current.resolve(second.markerHandle))
      assertTrue(current.isValid(first.markerHandle))
      assertTrue(current.isValid(second.markerHandle))
    }
    assertEquals(PMarkerResolution.ABSENT, updated.remove(first.markerHandle).resolve(first.markerHandle))
    assertEquals(firstRange, root.resolve(first.markerHandle))
  }

  private fun resolutionMap(root: PMarkerRoot): Any? {
    val cache = checkNotNull(PMarkerDirectRoot::class.java.getDeclaredField("cachedResolutions")
                              .apply { isAccessible = true }.get(root))
    return cache.javaClass.getDeclaredField("additionalResolutions").apply { isAccessible = true }.get(cache)
  }

  @Test
  fun `marker getters follow the snapshot during publication`() {
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

  @Test
  fun `disposal during publication clears the store cache`() {
    val core = DocumentImpl("abcdefgh", true).core
    var current = core.snapshot()
    val document = DocumentImpl(object : DocumentCore by core {
      override fun snapshot(): DocumentBranch = current
    }, null)
    val store = SnapshotMarkerRootStore(document, SnapshotRangeMarkerImpl::class.java)
    val before = current
    store.rootReference(before)
    val cache = checkNotNull(store.currentRootCache.get())
    val patch = DocumentPatch.simple(0, 0, "xx", newModStamp = 1, clearLineFlags = false)
    var after = before
    for (op in patch.ops()) after = after.applyOp(op)

    assertTrue(document.snapshotMarkerStores.tryCommit(before, after, patch, isCurrent = { current === before }, publish = {
      current = after
      store.dispose(document.snapshotMarkerStores)
    }))

    assertNull(cache.get(after))
    assertNull(store.root(after))
  }

  @Test
  fun `updates to a previous snapshot preserve the current root`() {
    val document = DocumentImpl("abcdefgh", true)
    val marker = document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
    val store = document.rangeMarkers.rootStore()
    val previous = document.core.snapshot()
    document.insertString(0, "x")
    val current = document.core.snapshot()
    val currentRoot = checkNotNull(store.root(current))

    store.updateRoot(previous) { it.remove(marker.markerHandle) }

    assertFalse(checkNotNull(store.root(previous)).isValid(marker.markerHandle))
    assertSame(currentRoot, store.root(current))
    assertTrue(marker.isValid)
    assertEquals(TextRange(3, 5), marker.textRange)
  }

  @Test
  fun `a failed snapshot publication preserves marker offsets`() {
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

  @Test
  fun `current root cache releases earlier snapshots`() {
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

  @Test
  fun `a retained store does not retain the current snapshot`() {
    val (store, snapshot) = createStoreWithWeakSnapshot()

    GCUtil.tryGcSoftlyReachableObjects { snapshot.get() == null }

    assertNull(snapshot.get())
    ObjectUtilsRt.reachabilityFence(store)
  }

  private fun createStoreWithWeakSnapshot(): Pair<SnapshotMarkerRootStore<*>, WeakReference<com.intellij.openapi.editor.ex.DocumentSnapshot>> {
    val document = DocumentImpl("abcdefgh", true)
    val marker = document.createRangeMarker(2, 4)
    assertEquals(2, marker.startOffset)
    return document.rangeMarkers.rootStore() to WeakReference(document.core.snapshot())
  }

  @Test
  fun `validity checks do not resolve offsets`() {
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

  @Test
  fun `root insertion does not need a live marker for its policy`() {
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val marker = DocumentImpl("", true).createRangeMarker(0, 0) as SnapshotRangeMarkerImpl
    val handle = SnapshotMarkerReference.create(marker, false)
    (handle as Reference<*>).clear()
    val policy = MarkerPolicy { _, _, _, _ -> error("Insertion must not transform the marker") }
    val root = PMarkerRoot.empty().insert(handle, 2, 4, spec, flavorFlags = 0, policy = policy)

    assertEquals(TextRangeScalarUtil.toScalarRange(2, 4), root.resolve(handle))
    assertSame(policy, root.overlappingIterator(2, 4, 0).next().policy)
  }

  @Test
  fun `rotations preserve roots across shifted insertions and removals`() {
    val document = DocumentImpl("x".repeat(512), true)
    val marker = document.createRangeMarker(0, 0) as SnapshotRangeMarkerImpl
    val handles = List(128) { SnapshotMarkerReference.create(marker, true) }
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    var original = PMarkerRoot.empty()
    for (index in 63 downTo 0) {
      val start = index * 4 + 1
      original = original.insert(handles[index], start, start + 1, spec, flavorFlags = 0)
    }
    val before = document.core.snapshot().text()
    val patch = DocumentPatch.simple(0, 0, "xx", newModStamp = 1, clearLineFlags = false)
    val shifted = original.applyPatch(patch, before, before.replace(0, 0, "xx"))
    var changed = shifted
    for (index in 64 until handles.size) {
      val start = (index % 64) * 4 + 3
      changed = changed.insert(handles[index], start, start + 1, spec, flavorFlags = 0)
    }
    for (index in handles.indices step 2) changed = changed.remove(handles[index])
    val fork = original.remove(handles[31])

    for ([index, handle] in handles.withIndex()) {
      val start = (index % 64) * 4 + 1
      val range = TextRangeScalarUtil.toScalarRange(start, start + 1)
      val shiftedRange = TextRangeScalarUtil.toScalarRange(start + 2, start + 3)
      assertEquals(if (index < 64) range else PMarkerResolution.ABSENT, original.resolve(handle))
      assertEquals(if (index < 64) shiftedRange else PMarkerResolution.ABSENT, shifted.resolve(handle))
      assertEquals(if (index % 2 == 1) shiftedRange else PMarkerResolution.ABSENT, changed.resolve(handle))
      assertEquals(if (index < 64 && index != 31) range else PMarkerResolution.ABSENT, fork.resolve(handle))
    }
  }

  @Test
  fun `copied trees inherit unchanged parent records`() {
    val marker = DocumentImpl("", true).createRangeMarker(0, 0) as SnapshotRangeMarkerImpl
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val entries = List(15) { index ->
      PMarkerRoot.MarkerEntry(SnapshotMarkerReference.create(marker, true), index * 4 + 1, index * 4 + 2, spec, 0)
    }
    val root = PMarkerDirectRoot.empty().insertMany(entries)
    val nodeField = PMarkerDirectRoot::class.java.getDeclaredField("root").apply { isAccessible = true }
    val latestField = PMarkerNodeHistory::class.java.getDeclaredField("latest").apply { isAccessible = true }
    val node = nodeField.get(root) as PMarkerNode
    val child = checkNotNull(node.left)
    val parentRecord = checkNotNull(latestField.get(child.state.parents))

    val added = SnapshotMarkerReference.create(marker, true)
    val updated = root.insert(added, 61, 62, spec, 0)
    val copied = nodeField.get(updated) as PMarkerNode

    assertSame(node.state, copied.state)
    assertSame(child.state, checkNotNull(copied.left).state)
    assertSame(parentRecord, latestField.get(child.state.parents))
    assertEquals(TextRangeScalarUtil.toScalarRange(61, 62), updated.resolve(added))
  }

  @Test
  fun `policy survives changes to marker flags`() {
    val document = DocumentImpl("abcdefgh", true)
    val policy = MarkerPolicy { entry, _, _, _ -> MarkerTransformResult(entry.nodeStart, 6) }
    val marker = SnapshotRangeMarkerImpl.createRangeMarker(
      document, 2, 4, MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false), policy = policy,
    )

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

  @Test
  fun `absent marker getters use creation offsets instead of cached offsets`() {
    val document = DocumentImpl("abcdefgh", true)
    val marker = document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
    val reference = marker.currentRootReference()
    val before = document.core.snapshot().text()
    val patch = DocumentPatch.simple(0, 0, "xx", newModStamp = 1, clearLineFlags = false)
    val shifted = reference.get().applyPatch(patch, before, before.replace(0, 0, "xx"))
    reference.set(shifted)
    assertEquals(TextRange(4, 6), marker.textRange)

    reference.set(shifted.remove(marker.markerHandle))

    repeat(2) {
      assertEquals(PMarkerResolution.ABSENT, marker.resolve(reference.get()))
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
      assertEquals(PMarkerResolution.ABSENT, marker.resolve(reference.get()))
      assertFalse(marker.isValid)
      assertEquals(4, marker.startOffset)
      assertEquals(6, marker.endOffset)
      assertEquals(TextRange(4, 6), marker.textRange)
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
    val document = DocumentImpl("abcdefgh", true)
    val marker = document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
    val root = marker.currentRootReference().get()
    val resolution = root.resolve(marker.markerHandle)
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val otherRoot = if (absent) root.remove(marker.markerHandle)
    else PMarkerRoot.empty().insert(marker.markerHandle, 4, 6, spec, flavorFlags = 0)
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
          assertEquals(2, marker.startOffset)
          assertEquals(4, marker.endOffset)
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
