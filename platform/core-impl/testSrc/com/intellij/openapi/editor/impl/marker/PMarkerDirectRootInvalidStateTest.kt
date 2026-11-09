// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.testFramework.junit5.UsePMarkerImplementation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@UsePMarkerImplementation
internal class PMarkerDirectRootInvalidStateTest {
  @Test
  fun `invalid states survive later insertions and shifts`() = withEntries { entries ->
    val before = DocumentImpl("x".repeat(200), true).core.snapshot().text()
    val deletion = DocumentPatch.simple(0, 100, "", 1, false)
    val after = before.replace(0, 100, "")
    for (count in listOf(1, 3)) {
      val original = PMarkerDirectRoot.empty().insertMany(entries.take(count))
      val invalidated = original.applyPatch(deletion, before, after)
      val extended = invalidated.insertMany(entries.drop(count))
      val insertion = DocumentPatch.simple(0, 0, "xx", 2, false)
      val shifted = extended.applyPatch(insertion, after, after.replace(0, 0, "xx"))

      for (entry in entries.take(count)) {
        assertEquals(entry.range(), original.resolve(entry.handle))
        val invalidRange = PMarkerResolution.invalid(entry.range())
        assertEquals(invalidRange, invalidated.resolve(entry.handle))
        assertEquals(invalidRange, extended.resolve(entry.handle))
        assertEquals(invalidRange, shifted.resolve(entry.handle))
      }
      for (entry in entries.drop(count)) {
        assertEquals(entry.range(), extended.resolve(entry.handle))
        assertEquals(TextRangeScalarUtil.shift(entry.range(), 2), shifted.resolve(entry.handle))
      }
    }
  }

  @Test
  fun `removal tombstones and bulk purges preserve branches`() = withEntries { entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries)
    for (count in listOf(1, 3)) {
      val removedEntries = entries.take(count)
      val handles = removedEntries.map { it.handle }
      val removed = removedEntries.fold(original) { root, entry -> root.remove(entry.handle) }
      val merged = removed.mergeValidMarkersFrom(original)
      val restored = removed.restoreEntries(removedEntries)

      for (entry in entries) {
        assertEquals(entry.range(), original.resolve(entry.handle))
        assertEquals(entry.range(), restored.resolve(entry.handle))
        val expected = if (entry in removedEntries) PMarkerResolution.ABSENT else entry.range()
        assertEquals(expected, removed.resolve(entry.handle))
        assertEquals(expected, merged.resolve(entry.handle))
      }
      for (source in listOf(original, removed)) {
        val purged = source.purgeMany(handles)
        val mergedAfterPurge = purged.mergeValidMarkersFrom(original)
        for (entry in entries) {
          val expected = if (entry in removedEntries) PMarkerResolution.ABSENT else entry.range()
          assertEquals(expected, purged.resolve(entry.handle))
          assertEquals(entry.range(), mergedAfterPurge.resolve(entry.handle))
        }
      }
    }
  }

  private fun MarkerEntry.range(): Long = TextRangeScalarUtil.toScalarRange(nodeStart, nodeEnd)

  private fun withEntries(test: (List<MarkerEntry>) -> Unit) {
    val marker = DocumentImpl("", true).createRangeMarker(0, 0) as SnapshotRangeMarkerImpl
    val entries = List(5) { index ->
      val start = (index + 1) * 10
      MarkerEntry(SnapshotMarkerReference.create(marker, true), start, start + 2,
                  MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false), flavorFlags = 0)
    }
    test(entries)
  }
}
