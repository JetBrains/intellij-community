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
internal class PMarkerDirectRootRemovalTest {
  @Test
  fun `removal hints preserve identity and offsets across branches and lazy shifts`() = withEntries { document, entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries)
    val before = document.core.snapshot().text()
    val patch = DocumentPatch.simple(0, 0, "xxx", 1, false)
    val shifted = original.applyPatch(patch, before, before.replace(0, 0, "xxx"))
    val fork = original.remove(entries.last().handle)

    for ([source, delta] in listOf(original to 0, shifted to 3, fork to 0)) {
      var current = source
      for ([index, entry] in entries.dropLast(1).withIndex()) {
        val hint = when (index % 3) {
          0 -> entry.nodeStart + delta
          1 -> entry.nodeStart + 10
          else -> Int.MAX_VALUE
        }
        val expected = TextRangeScalarUtil.shift(entry.range(), delta)
        assertEquals(expected, current.resolveForRemoval(entry.handle, hint))
        current = current.remove(entry.handle)
        assertEquals(PMarkerResolution.ABSENT, current.resolve(entry.handle))
        assertEquals(expected, source.resolve(entry.handle))
      }
      val remaining = entries.last()
      val expected = if (source === fork) PMarkerResolution.ABSENT else TextRangeScalarUtil.shift(remaining.range(), delta)
      assertEquals(expected, current.resolveForRemoval(remaining.handle, remaining.nodeStart + delta))
    }
    for (entry in entries) {
      assertEquals(entry.range(), original.resolve(entry.handle))
      assertEquals(TextRangeScalarUtil.shift(entry.range(), 3), shifted.resolve(entry.handle))
    }
  }

  @Test
  fun `removal hints preserve invalid and absent states`() = withEntries { document, entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries)
    val before = document.core.snapshot().text()
    val patch = DocumentPatch.simple(0, before.length(), "", 1, false)
    val invalidated = original.applyPatch(patch, before, before.replace(0, before.length(), ""))
    val removed = original.remove(entries.first().handle)
    val empty = PMarkerDirectRoot.empty()
    for (entry in entries) {
      assertEquals(PMarkerResolution.invalid(entry.range()), invalidated.resolveForRemoval(entry.handle, entry.nodeStart))
      assertEquals(PMarkerResolution.ABSENT, empty.resolveForRemoval(entry.handle, entry.nodeStart))
    }
    val first = entries.first()
    assertEquals(PMarkerResolution.ABSENT, removed.resolveForRemoval(first.handle, first.nodeStart))
    assertEquals(first.range(), original.resolveForRemoval(first.handle, first.nodeStart))
  }

  private fun MarkerEntry.range(): Long = TextRangeScalarUtil.toScalarRange(nodeStart, nodeEnd)

  private fun withEntries(test: (DocumentImpl, List<MarkerEntry>) -> Unit) {
    val document = DocumentImpl("x".repeat(200), true)
    val marker = document.createRangeMarker(0, 0) as SnapshotRangeMarkerImpl
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val entries = List(36) { index ->
      val start = index / 3 * 10 + 5
      MarkerEntry(SnapshotMarkerReference.create(marker, true), start, start + index % 3, spec, flavorFlags = 0)
    }
    test(document, entries)
  }
}
