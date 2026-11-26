// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.testFramework.junit5.UsePMarkerImplementation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.function.Consumer

@UsePMarkerImplementation
internal class PMarkerRelocationRootTest {
  @Test
  fun `descending edits preserve every intermediate root and affected marker`(): Unit = checkTyping(reverse = true)

  @Test
  fun `ascending edits preserve every intermediate root and affected marker`(): Unit = checkTyping(reverse = false)

  @Test
  fun `ordered insertions without caret moves match an eager tree`(): Unit = withEntries { document, entries ->
    for (reverse in listOf(false, true)) {
      for (replace in listOf(false, true)) checkOrderedInsertions(document, entries, reverse, replace)
    }
  }

  private fun checkOrderedInsertions(document: DocumentImpl, entries: List<MarkerEntry>, reverse: Boolean, replace: Boolean) {
    val order = if (reverse) entries.asReversed() else entries
    val moved = order.first().let { it.copy(nodeStart = it.nodeStart + 1, nodeEnd = it.nodeEnd + 1) }
    val original = PMarkerDirectRoot.empty().insertMany(entries)
    var pending = PMarkerRelocationRoot.move(original, moved)
    var eager: PMarkerRoot = original.restoreEntries(listOf(moved))
    var text = document.core.snapshot().text()
    val versions = ArrayList<Pair<PMarkerRoot, PMarkerRoot>>()
    for ((index, entry) in order.drop(1).withIndex()) {
      val offset = PMarkerResolution.startOffset(eager.resolve(entry.handle))
      val fragment = "a".repeat(index % 3 + 1)
      val patch = DocumentPatch.simple(offset, offset, fragment, index + 1L, false)
      val after = text.replace(offset, offset, fragment)
      eager = eager.applyPatch(patch, text, after)
      pending = pending.applyPatch(patch, text, after)
      versions.add(eager to pending)
      text = after
    }
    assertTrue(pending is PMarkerRelocationRoot)
    for ((expected, actual) in versions) {
      for (entry in entries) assertEquals(expected.resolve(entry.handle), actual.resolve(entry.handle))
    }
    val oppositeOffset = if (reverse) text.length() - 1 else 0
    val patch = if (replace) DocumentPatch.simple(2, 4, "z", 50, false)
    else DocumentPatch.simple(oppositeOffset, oppositeOffset, "z", 50, false)
    val after = text.replace(patch.startOffset(), patch.endOffset(), patch.newFragment())
    val expected = eager.applyPatch(patch, text, after)
    val actual = pending.applyPatch(patch, text, after)
    for (entry in entries) assertEquals(expected.resolve(entry.handle), actual.resolve(entry.handle))
    val finished = runInBatch(document, pending)
    for (entry in entries) assertEquals(eager.resolve(entry.handle), finished.resolve(entry.handle))
  }

  private fun checkTyping(reverse: Boolean): Unit = withEntries { document, entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries)
    var current: PMarkerRoot = original
    var text = document.core.snapshot().text()
    val versions = ArrayList<Pair<List<Int>, PMarkerRoot>>()
    val offsets = entries.map { it.nodeStart }.toMutableList()
    val order = if (reverse) entries.indices.reversed() else entries.indices
    for (index in order) {
      val entry = entries[index]
      val offset = offsets[index]
      val patch = DocumentPatch.simple(offset, offset, "a", index + 1L, false)
      val after = text.replace(offset, offset, "a")
      val affected = ArrayList<SnapshotMarkerReference<*>>()
      current = current.applyPatch(patch, text, after, PMarkerRoot.EMPTY_HANDLE_CONSUMER, Consumer { affected.add(it) })
      assertEquals(listOf(entry.handle), affected)
      val beforeMove = current
      current = PMarkerRelocationRoot.move(current, entry.copy(nodeStart = offset + 1, nodeEnd = offset + 1))
      assertEquals(TextRangeScalarUtil.toScalarRange(offset, offset), beforeMove.resolve(entry.handle))
      for (other in offsets.indices) if (offsets[other] > offset) offsets[other]++
      offsets[index] = offset + 1
      versions.add(offsets.toList() to current)
      text = after
    }
    assertTrue(current is PMarkerRelocationRoot)
    val finished = runInBatch(document, current)
    assertTrue(finished is PMarkerDirectRoot)
    for ((expected, root) in versions) {
      for ((index, entry) in entries.withIndex()) {
        assertEquals(TextRangeScalarUtil.toScalarRange(expected[index], expected[index]), root.resolve(entry.handle))
        assertEquals(entry.range(), original.resolve(entry.handle))
      }
    }
    for ((index, entry) in entries.withIndex()) {
      assertEquals(TextRangeScalarUtil.shift(entry.range(), index + 1), finished.resolve(entry.handle))
    }
  }

  @Test
  fun `conflicting edits match an eager tree`(): Unit = withEntries { document, entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries)
    val replacements = entries.take(3).map { it.copy(nodeStart = it.nodeStart + 1, nodeEnd = it.nodeEnd + 1) }
    val eager = original.restoreEntries(replacements)
    val before = document.core.snapshot().text()
    for (patch in listOf(
      DocumentPatch.simple(10, 10, "x", 1, false),
      DocumentPatch.simple(11, 11, "x", 1, false),
      DocumentPatch.simple(0, 3, "", 1, false),
      DocumentPatch.simple(0, before.length(), "short", 1, false),
    )) {
      var pending: PMarkerRoot = original
      for (entry in replacements) pending = PMarkerRelocationRoot.move(pending, entry)
      val after = before.replace(patch.startOffset(), patch.endOffset(), patch.newFragment())
      val expected = eager.applyPatch(patch, before, after)
      val actual = pending.applyPatch(patch, before, after)
      for (entry in entries) assertEquals(expected.resolve(entry.handle), actual.resolve(entry.handle))
    }
  }

  @Test
  fun `merging the base preserves the pending batch`(): Unit = withEntries { document, entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries)
    val moved = entries.last().copy(nodeStart = 150, nodeEnd = 150)
    val pending = PMarkerRelocationRoot.move(original, moved)
    assertSame(pending, pending.mergeValidMarkersFrom(original))
    val before = document.core.snapshot().text()
    val patch = DocumentPatch.simple(0, 0, "x", 1, false)
    val after = pending.applyPatch(patch, before, before.replace(0, 0, "x"))
    assertTrue(after is PMarkerRelocationRoot)
    assertEquals(TextRangeScalarUtil.toScalarRange(151, 151), after.resolve(moved.handle))
  }

  @Test
  fun `point eligibility follows range and measure changes`(): Unit = withEntries { _, entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries)
    val handle = entries.first().handle
    assertTrue(original.canDeferPointInsertions)
    val measured = original.updateMeasure(handle, 1)
    assertFalse(measured.canDeferPointInsertions)
    assertTrue(measured.updateMeasure(handle, 0).canDeferPointInsertions)
    val range = entries.first().copy(nodeEnd = entries.first().nodeEnd + 2)
    val ranged = original.restoreEntries(listOf(range))
    assertFalse(ranged.canDeferPointInsertions)
    assertTrue(ranged.restoreEntries(listOf(entries.first())).canDeferPointInsertions)
    val persistent = entries.first().copy(policy = PersistentMarkerPolicy)
    assertFalse(original.restoreEntries(listOf(persistent)).canDeferPointInsertions)
    assertTrue(original.canDeferPointInsertions)
  }

  @Test
  fun `mixed markers preserve relocations across insertions`(): Unit = withEntries { document, entries ->
    for (policy in listOf(DefaultMarkerPolicy, PersistentMarkerPolicy)) {
      val range = entries.last().copy(nodeEnd = entries.last().nodeEnd + 2, policy = policy)
      val original = PMarkerDirectRoot.empty().insertMany(entries.dropLast(1) + range)
      val moved = entries.first().copy(nodeStart = 11, nodeEnd = 11)
      val pending = PMarkerRelocationRoot.move(original, moved)
      val eager = original.restoreEntries(listOf(moved))
      val before = document.core.snapshot().text()
      val patch = DocumentPatch.simple(30, 30, "a", 1, false)
      val after = before.replace(30, 30, "a")
      val expected = eager.applyPatch(patch, before, after)
      val actual = pending.applyPatch(patch, before, after)
      assertEquals(policy.isPersistent, actual is PMarkerDirectRoot)
      for (entry in entries) assertEquals(expected.resolve(entry.handle), actual.resolve(entry.handle))
      assertEquals(moved.range(), pending.resolve(moved.handle))
    }
  }

  @Test
  fun `queries and subsequent moves preserve pending positions`(): Unit = withEntries { document, entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries)
    val first = entries.last().copy(nodeStart = 150, nodeEnd = 150)
    val pending = PMarkerRelocationRoot.move(original, first)
    assertEquals(entries.size, pending.overlappingIterator(0, 200, 0).asSequence().count())
    val second = entries.first().copy(nodeStart = 5, nodeEnd = 5)
    val resumed = PMarkerRelocationRoot.move(pending, second)
    val finished = runInBatch(document, resumed)
    for (root in listOf(resumed, finished)) {
      assertEquals(first.range(), root.resolve(first.handle))
      assertEquals(second.range(), root.resolve(second.handle))
    }
    assertEquals(entries.first().range(), pending.resolve(second.handle))
    assertEquals(entries.last().range(), original.resolve(first.handle))
  }

  @Test
  fun `direct roots merge pending positions`(): Unit = withEntries { _, entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries.dropLast(1))
    val entry = entries.last()
    val other = PMarkerDirectRoot.empty().insertMany(listOf(entry))
    val replacement = entry.copy(nodeStart = 180, nodeEnd = 180)
    val pending = PMarkerRelocationRoot.move(other, replacement)
    val merged = original.mergeValidMarkersFrom(pending)
    assertEquals(replacement.range(), merged.resolve(entry.handle))
    assertEquals(PMarkerResolution.ABSENT, original.resolve(entry.handle))
    assertEquals(entry.range(), other.resolve(entry.handle))
  }

  private fun MarkerEntry.range(): Long = TextRangeScalarUtil.toScalarRange(nodeStart, nodeEnd)

  private fun runInBatch(document: DocumentImpl, root: PMarkerRoot): PMarkerRoot {
    val store = SnapshotMarkerRootStore(document, SnapshotRangeMarkerImpl::class.java, emptyRoot = root)
    try {
      store.currentRootReference()
      PMarkerRelocationRoot.runInBatch(store) { }
      return store.currentRootReference().get()
    }
    finally {
      store.dispose(document.snapshotMarkerStores)
    }
  }

  private fun withEntries(test: (DocumentImpl, List<MarkerEntry>) -> Unit) {
    val document = DocumentImpl("x".repeat(500), true)
    val marker = document.createRangeMarker(0, 0) as SnapshotRangeMarkerImpl
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val entries = List(32) { index ->
      val offset = 10 + index * 4
      MarkerEntry(SnapshotMarkerReference.create(marker, true), offset, offset, spec, 0)
    }
    test(document, entries)
  }
}
