// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.testFramework.junit5.UsePMarkerImplementation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer

@UsePMarkerImplementation
internal class PMarkerInsertionRootTest {
  @Test
  fun `ordered insertions preserve intermediate ranges queries and callbacks`(): Unit = withEntries { document, entries ->
    for (reverse in listOf(false, true)) {
      val original = PMarkerDirectRoot.empty().insertMany(entries)
      var eager: PMarkerRoot = original
      val versions = ArrayList<Pair<PMarkerRoot, PMarkerRoot>>()
      val finished = runInBatch(document, original) { initial ->
        var pending = initial
        var text = document.core.snapshot().text()
        val order = entries.filter { it.nodeStart > 6 && it.nodeEnd < 490 }.let { if (reverse) it.asReversed() else it }
        for ((index, entry) in order.withIndex()) {
          val offset = PMarkerResolution.startOffset(eager.resolve(entry.handle)) + 3
          val fragment = listOf("a", "\r\n", "\uD83D\uDE00")[index % 3]
          val patch = DocumentPatch.simple(offset, offset, fragment, index + 1L, false)
          val after = text.replace(offset, offset, fragment)
          val expectedAffected = entries.filter {
            val range = eager.resolve(it.handle)
            PMarkerResolution.startOffset(range) < offset && offset < PMarkerResolution.endOffset(range)
          }.map { it.handle }
          val actualAffected = ArrayList<SnapshotMarkerReference<*>>()
          eager = eager.applyPatch(patch, text, after)
          pending = pending.applyPatch(patch, text, after, affectedMarkerConsumer = Consumer { actualAffected.add(it) })
          assertTrue(pending is PMarkerInsertionRoot)
          assertEquals(expectedAffected.toSet(), actualAffected.toSet())
          assertQueries(eager, pending, listOf(offset, offset + 1, offset + fragment.length, after.length()))
          versions.add(eager to pending)
          text = after
        }
        pending
      }
      for ((expected, actual) in versions) assertRanges(expected, actual, entries)
      assertTrue(finished is PMarkerDirectRoot)
      assertRanges(eager, finished, entries)
      assertRanges(PMarkerDirectRoot.empty().insertMany(entries), original, entries)
    }
  }

  @Test
  fun `boundary edits replacements and changes of direction use the complete tree`(): Unit = withEntries { document, entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries)
    val before = document.core.snapshot().text()
    val first = DocumentPatch.simple(13, 13, "ab", 1, false)
    val middle = before.replace(13, 13, "ab")
    val second = DocumentPatch.simple(27, 27, "c", 2, false)
    val text = middle.replace(27, 27, "c")
    val eager = original.applyPatch(first, before, middle).applyPatch(second, middle, text)
    val patches = listOf(
      Triple(10, 10, "x"), Triple(20, 20, "\uDE00"), Triple(27, 27, "x"),
      Triple(4, 4, "x"), Triple(25, 28, ""), Triple(12, 32, "replacement"),
    ).map { (start, end, fragment) -> DocumentPatch.simple(start, end, fragment, 3, false) } +
                  DocumentPatch.complex(60, 60, text.luckyChars().subSequence(10, 20), 3, false, 60, 60, 10)
    runInBatch(document, original) { initial ->
      val pending = initial.applyPatch(first, before, middle).applyPatch(second, middle, text)
      assertTrue(pending is PMarkerInsertionRoot)
      for (patch in patches) {
        val after = text.replace(patch.startOffset(), patch.endOffset(), patch.newFragment())
        val expected = eager.applyPatch(patch, text, after)
        val actual = pending.applyPatch(patch, text, after)
        assertTrue(actual is PMarkerDirectRoot)
        assertRanges(expected, actual, entries)
        assertQueries(expected, actual, listOf(patch.startOffset(), patch.startOffset() + patch.newFragment().length, after.length()))
      }
      assertRanges(eager, pending, entries)
      pending
    }
  }

  @Test
  fun `branches from a buffered root remain independent after publication`(): Unit = withEntries { document, entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries).remove(entries.last().handle)
    val before = document.core.snapshot().text()
    val first = DocumentPatch.simple(13, 13, "ab", 1, false)
    val middle = before.replace(13, 13, "ab")
    val eager = original.applyPatch(first, before, middle)
    val branches = ArrayList<Pair<PMarkerRoot, PMarkerRoot>>()
    val finished = runInBatch(document, original) { initial ->
      val pending = initial.applyPatch(first, before, middle)
      for (offset in listOf(4, 27)) {
        val patch = DocumentPatch.simple(offset, offset, "branch", 2, false)
        val after = middle.replace(offset, offset, "branch")
        branches.add(eager.applyPatch(patch, middle, after) to pending.applyPatch(patch, middle, after))
      }
      for ((expected, actual) in branches) {
        assertTrue(actual is PMarkerInsertionRoot)
        assertQueries(expected, actual, listOf(4, 13, 27, 100, 510))
      }
      assertRanges(eager, pending, entries)
      branches.last().second
    }
    for ((expected, actual) in branches) assertRanges(expected, actual, entries)
    assertRanges(branches.last().first, finished, entries)
  }

  @Test
  fun `marker mutations preserve the pending snapshot`(): Unit = withEntries { document, entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries)
    val before = document.core.snapshot().text()
    val patch = DocumentPatch.simple(13, 13, "abc", 1, false)
    val after = before.replace(13, 13, "abc")
    val eager = original.applyPatch(patch, before, after)
    val handle = entries[1].handle
    val mutations = listOf<(PMarkerRoot) -> PMarkerRoot>(
      { it.remove(handle) },
      { it.purgeMany(listOf(handle)) },
      { it.updateFlavor(handle, 7) },
      { it.updateSpec(handle, MarkerSpec.create(isGreedyToLeft = true, isGreedyToRight = true)) },
      { it.updateMeasure(entries.last().handle, 4) },
      { it.restoreEntries(listOf(entries[1].copy(nodeStart = 400, nodeEnd = 405))) },
    )
    runInBatch(document, original) { initial ->
      val pending = initial.applyPatch(patch, before, after)
      for (mutation in mutations) {
        val expected = mutation(eager)
        val actual = mutation(pending)
        assertRanges(expected, actual, entries)
        assertQueries(expected, actual, listOf(0, 6, 13, 25, 400, 490, 503))
        assertRanges(eager, pending, entries)
      }
      val additional = entries[1].copy(handle = SnapshotMarkerReference.create(entries[1].marker!!, true), nodeStart = 450, nodeEnd = 455)
      val inserted = pending.insertMany(listOf(additional))
      val other = PMarkerDirectRoot.empty().insertMany(listOf(additional))
      assertRanges(eager.insertMany(listOf(additional)), inserted, entries + additional)
      assertRanges(eager.mergeValidMarkersFrom(other), pending.mergeValidMarkersFrom(other), entries + additional)
      assertSame(pending, pending.mergeValidMarkersFrom(original))
      assertSame(pending, pending.mergeValidMarkersFrom(pending))
      pending
    }
  }

  @Test
  fun `policies that change validity or growth use the complete tree`(): Unit = withEntries { document, entries ->
    val before = document.core.snapshot().text()
    val patch = DocumentPatch.simple(13, 13, "x", 1, false)
    val after = before.replace(13, 13, "x")
    for (policy in listOf(
      MarkerPolicy { entry, _, _, _ -> MarkerTransformResult(entry) },
      MarkerPolicy { _, _, _, _ -> MarkerTransformResult(MarkerTransformError.INVALIDATED_BY_EDIT) },
    )) {
      val source = entries.mapIndexed { index, entry -> if (index == 1) entry.copy(policy = policy) else entry }
      val original = PMarkerDirectRoot.empty().insertMany(source)
      val expected = original.applyPatch(patch, before, after)
      val actual = runInBatch(document, original) { it.applyPatch(patch, before, after) }
      assertTrue(actual is PMarkerDirectRoot)
      assertRanges(expected, actual, source)
    }
  }

  @Test
  fun `tail insertions need no log before or after a buffered edit`(): Unit = withEntries { document, entries ->
    val source = listOf(entries[1])
    val original = PMarkerDirectRoot.empty().insertMany(source)
    val before = document.core.snapshot().text()
    val tail = DocumentPatch.simple(40, 40, "tail", 1, false)
    val middle = before.replace(40, 40, "tail")
    val interior = DocumentPatch.simple(13, 13, "x", 2, false)
    val after = middle.replace(13, 13, "x")
    val expected = original.applyPatch(interior, middle, after)
    val finished = runInBatch(document, original) { pending ->
      assertSame(pending, pending.applyPatch(tail, before, middle))
      val actual = pending.applyPatch(interior, middle, after)
      val later = DocumentPatch.simple(after.length(), after.length(), "more", 3, false)
      assertSame(actual, actual.applyPatch(later, after, after.replace(after.length(), after.length(), "more")))
      actual
    }
    assertRanges(expected, finished, source)
  }

  @Test
  fun `empty persistent and unchanged roots need no batch`(): Unit = withEntries { document, entries ->
    val empty = PMarkerDirectRoot.empty()
    val original = empty.insertMany(entries)
    val persistent = empty.insertMany(listOf(entries[1].copy(policy = PersistentMarkerPolicy)))
    for (root in listOf(empty, persistent)) {
      assertSame(root, runInBatch(document, root) { assertSame(root, it); it })
    }
    val before = document.core.snapshot().text()
    val finished = runInBatch(document, original) { pending ->
      assertSame(pending, pending.applyPatch(DocumentPatch.simple(13, 13, "", 1, false), before, before))
      pending
    }
    assertSame(original, finished)
  }

  private fun runInBatch(document: DocumentImpl, root: PMarkerRoot, action: (PMarkerRoot) -> PMarkerRoot): PMarkerRoot {
    val store = SnapshotMarkerRootStore(document, SnapshotRangeMarkerImpl::class.java, emptyRoot = root)
    try {
      store.runInBatch {
        val reference = store.currentRootReference()
        reference.set(action(reference.get()))
      }
      return store.currentRootReference().get()
    }
    finally {
      store.dispose(document.snapshotMarkerStores)
    }
  }

  private fun assertRanges(expected: PMarkerRoot, actual: PMarkerRoot, entries: List<MarkerEntry>) {
    for (entry in entries) {
      assertEquals(expected.resolve(entry.handle), actual.resolve(entry.handle), "handle " + entry.handle.id)
      assertEquals(expected.isValid(entry.handle), actual.isValid(entry.handle))
    }
    val expectedEntries = ArrayList<MarkerEntry>()
    val actualEntries = ArrayList<MarkerEntry>()
    expected.processAllRangeMarkers { entry, delta ->
      expectedEntries.add(entry.copy(nodeStart = entry.nodeStart + delta, nodeEnd = entry.nodeEnd + delta))
      true
    }
    actual.processAllRangeMarkers { entry, delta ->
      actualEntries.add(entry.copy(nodeStart = entry.nodeStart + delta, nodeEnd = entry.nodeEnd + delta))
      true
    }
    assertEquals(expectedEntries, actualEntries)
  }

  private fun assertQueries(expected: PMarkerRoot, actual: PMarkerRoot, offsets: List<Int>) {
    for (start in offsets) {
      assertEquals(expected.getPrefixAggregate(start), actual.getPrefixAggregate(start))
      for (end in listOf(start, start + 2, 600)) {
        for (flags in listOf(0, 1, 2)) {
          val expectedEntries = expected.overlappingIterator(start, end, flags).asSequence().toList()
          assertEquals(expectedEntries, actual.overlappingIterator(start, end, flags).asSequence().toList())
          val processed = ArrayList<MarkerEntry>()
          assertTrue(actual.processRangeMarkersOverlappingWith(start, end, flags) { entry, delta ->
            processed.add(entry.copy(nodeStart = entry.nodeStart + delta, nodeEnd = entry.nodeEnd + delta))
            true
          })
          assertEquals(expectedEntries, processed)
          assertEquals(expectedEntries.isEmpty(), actual.processRangeMarkersOverlappingWith(start, end, flags) { _, _ -> false })
        }
      }
    }
  }

  private fun withEntries(test: (DocumentImpl, List<MarkerEntry>) -> Unit) {
    val document = DocumentImpl("x".repeat(500), true)
    val marker = document.createRangeMarker(0, 0) as SnapshotRangeMarkerImpl
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    fun entry(start: Int, end: Int, flags: Byte = 3, measure: Int = 0): MarkerEntry =
      MarkerEntry(SnapshotMarkerReference.create(marker, true), start, end, spec, flags, measure)
    val entries = buildList {
      add(entry(0, 495))
      repeat(32) { index -> add(entry(10 + index * 12, 18 + index * 12, (index % 2 + 1).toByte())) }
      add(entry(6, 6, measure = 1))
      add(entry(490, 490, measure = 2))
    }
    test(document, entries)
  }
}
