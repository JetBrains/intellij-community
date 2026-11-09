// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.testFramework.junit5.UsePMarkerImplementation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@UsePMarkerImplementation
internal class PMarkerDirectRootShiftTest {
  private val before: DocumentText = DocumentImpl("x".repeat(200), true).core.snapshot().text()

  @Test
  fun `edits before all markers share both subtrees and preserve snapshots`() = withEntries { entries ->
    val root = PMarkerDirectRoot.empty().insertMany(entries)
    for (entry in entries) root.resolve(entry.handle)
    for (patch in listOf(
      DocumentPatch.simple(0, 0, "xx", 1, false),
      DocumentPatch.simple(0, 4, "", 1, false),
      DocumentPatch.simple(0, 4, "x", 1, false),
      DocumentPatch.simple(0, 4, "xxxx", 1, false),
    )) {
      val notifications = ArrayList<SnapshotMarkerReference<*>>()
      val after = before.replace(patch.startOffset(), patch.endOffset(), patch.newFragment())
      val edited = root.applyPatch(patch, before, after, { notifications.add(it) }, { notifications.add(it) })

      assertShift(root, edited, entries, patch.newFragment().length - patch.endOffset())
      assertTrue(notifications.isEmpty())
      if (edited !== root) {
        for (entry in entries) {
          assertEquals(edited.resolve(entry.handle), edited.advanceResolution(root.resolutionCacheIdentity, root.resolve(entry.handle)))
        }
      }
    }
  }

  @Test
  fun `edits at non greedy range starts share both subtrees`() = withEntries { entries ->
    val root = PMarkerDirectRoot.empty().insertMany(entries)
    for (patch in listOf(
      DocumentPatch.simple(20, 20, "xx", 1, false),
      DocumentPatch.simple(18, 20, "", 1, false),
      DocumentPatch.simple(18, 20, "x", 1, false),
      DocumentPatch.simple(18, 20, "xx", 1, false),
    )) {
      val edited = apply(root, patch)
      assertShift(root, edited, entries, patch.newFragment().length - (patch.endOffset() - patch.startOffset()))
    }
  }

  @Test
  fun `boundary flags and point markers retain their policy behavior`() = withEntries { entries ->
    for (point in listOf(false, true)) {
      val ranges = entries.mapIndexed { index, entry -> if (point && index == 1) entry.copy(nodeEnd = entry.nodeStart) else entry }
      val original = PMarkerDirectRoot.empty().insertMany(ranges)
      for (flags in 0..7) {
        val changedIndex = if (point) 1 else flags % 3
        val root = original.updateSpec(ranges[changedIndex].handle, flags.toByte())
        val updatedEntries = ranges.mapIndexed { index, entry -> if (index == changedIndex) entry.copy(spec = flags.toByte()) else entry }
        for (patch in listOf(
          DocumentPatch.simple(20, 20, "xx", 1, false),
          DocumentPatch.simple(18, 20, "", 1, false),
          DocumentPatch.complex(20, 20, "xx", 1, false, 18, 22, 20),
        )) {
          val after = before.replace(patch.startOffset(), patch.endOffset(), patch.newFragment())
          val edited = root.applyPatch(patch, before, after)
          for (entry in updatedEntries) {
            val expected = DefaultMarkerPolicy.transform(entry, patch, before, after)
            assertEquals(expected.range, edited.resolve(entry.handle), "point=$point, flags=$flags, patch=$patch")
          }
        }
      }
      val reset = original.updateSpec(ranges[1].handle, MarkerSpec.create(isGreedyToLeft = true, isGreedyToRight = false))
        .updateSpec(ranges[1].handle, MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false))
      if (!point) assertShift(reset, apply(reset, DocumentPatch.simple(20, 20, "xx", 1, false)), ranges, 2)
    }
  }

  @Test
  fun `boundary edits report affected markers and run custom policies`() = withEntries { entries ->
    val root = PMarkerDirectRoot.empty().insertMany(entries)
    val patch = DocumentPatch.simple(20, 20, "xx", 1, false)
    val affected = ArrayList<SnapshotMarkerReference<*>>()
    val edited = root.applyPatch(patch, before, before.replace(20, 20, "xx"), affectedMarkerConsumer = { affected.add(it) })
    assertEquals(entries.take(3).map { it.handle }, affected)
    for (entry in entries) assertEquals(TextRangeScalarUtil.shift(entry.range(), 2), edited.resolve(entry.handle))

    var transformed = 0
    val custom = entries[1].copy(policy = MarkerPolicy { _, _, _, _ ->
      transformed++
      MarkerTransformResult(MarkerTransformError.INVALIDATED_BY_EDIT)
    })
    val withCustom = root.restoreEntries(listOf(custom))
    val invalidated = apply(withCustom, patch)
    assertEquals(1, transformed)
    assertEquals(PMarkerResolution.invalid(custom.range()), invalidated.resolve(custom.handle))

    val shifted = apply(invalidated, DocumentPatch.simple(0, 0, "xx", 1, false))
    assertShift(invalidated, shifted, entries.filter { it.handle !== custom.handle }.map { it.shifted(2) }, 2)
    assertEquals(PMarkerResolution.invalid(custom.range()), shifted.resolve(custom.handle))
    assertEquals(custom.range(), withCustom.resolve(custom.handle))
  }

  @Test
  fun `persistent traversals and text moves bypass the whole tree shift`() = withEntries { entries ->
    var transformed = 0
    val persistent = entries[1].copy(policy = object : MarkerPolicy {
      override val isPersistent: Boolean = true

      override fun transform(
        entry: MarkerEntry,
        patch: DocumentPatch,
        beforeText: DocumentText,
        afterText: DocumentText,
      ): MarkerTransformResult {
        transformed++
        return MarkerTransformResult(entry)
      }
    })
    val root = PMarkerDirectRoot.empty().insertMany(entries).restoreEntries(listOf(persistent))
    val replaced = apply(root, DocumentPatch.complex(0, 1, "xx", 1, false, 0, before.length(), 0))
    assertEquals(1, transformed)
    assertEquals(persistent.range(), replaced.resolve(persistent.handle))

    val source = PMarkerDirectRoot.empty().insertMany(entries)
    val moved = apply(source, DocumentPatch.complex(0, 0, "xx", 1, false, 0, 0, 22))
    for (entry in entries) {
      val expected = if (entry.nodeStart == 20) TextRangeScalarUtil.toScalarRange(0, 2) else TextRangeScalarUtil.shift(entry.range(), 2)
      assertEquals(expected, moved.resolve(entry.handle))
      assertEquals(entry.range(), source.resolve(entry.handle))
    }
  }

  @Test
  fun `repeated shifts preserve metadata queries and later insertions and removals`() = withEntries { entries ->
    val original = PMarkerDirectRoot.empty().insertMany(entries)
    val patch = DocumentPatch.simple(0, 0, "xx", 1, false)
    var shifted = original
    repeat(8) { shifted = apply(shifted, patch) }
    assertShift(original, shifted, entries, 16)
    val removed = shifted.remove(entries.first().handle)
    val restored = removed.restoreEntries(listOf(entries.first().shifted(16)))
    for (entry in entries) {
      val range = TextRangeScalarUtil.shift(entry.range(), 16)
      assertEquals(if (entry === entries.first()) PMarkerResolution.ABSENT else range, removed.resolve(entry.handle))
      assertEquals(range, restored.resolve(entry.handle))
    }
    val withoutFirstRange = shifted.purgeMany(entries.take(3).map { it.handle })
    val nextBoundary = DocumentPatch.simple(40, 40, "xx", 1, false)
    assertShift(withoutFirstRange, apply(withoutFirstRange, nextBoundary), entries.drop(3).map { it.shifted(16) }, 2)
  }

  private fun apply(root: PMarkerDirectRoot, patch: DocumentPatch): PMarkerDirectRoot =
    root.applyPatch(patch, before, before.replace(patch.startOffset(), patch.endOffset(), patch.newFragment()))

  private fun assertShift(original: PMarkerDirectRoot, shifted: PMarkerDirectRoot, entries: List<MarkerEntry>, delta: Int) {
    if (delta == 0) assertSame(original, shifted)
    else {
      assertSame(node(original).left, node(shifted).left)
      assertSame(node(original).right, node(shifted).right)
    }
    for (entry in entries) {
      val range = TextRangeScalarUtil.shift(entry.range(), delta)
      assertEquals(range, shifted.resolve(entry.handle))
      assertEquals(entry.range(), original.resolve(entry.handle))
      assertEquals(entries.filter { it.nodeStart <= entry.nodeStart }.sumOf { it.measure },
                   shifted.getPrefixAggregate(entry.nodeStart + delta))
    }
    val found = ArrayList<MarkerEntry>()
    shifted.processRangeMarkersOverlappingWith(0, 200, 1) { found.add(it); true }
    assertEquals(entries.filter { it.flavorFlags.toInt() and 1 != 0 }.map { it.handle }, found.map { it.handle })
  }

  private fun node(root: PMarkerDirectRoot): PMarkerNode =
    PMarkerDirectRoot::class.java.getDeclaredField("root").apply { isAccessible = true }.get(root) as PMarkerNode

  private fun MarkerEntry.range(): Long = TextRangeScalarUtil.toScalarRange(nodeStart, nodeEnd)

  private fun MarkerEntry.shifted(delta: Int): MarkerEntry = copy(nodeStart = nodeStart + delta, nodeEnd = nodeEnd + delta)

  private fun withEntries(test: (List<MarkerEntry>) -> Unit) {
    val marker = DocumentImpl("", true).createRangeMarker(0, 0) as SnapshotRangeMarkerImpl
    val entries = List(31) { index ->
      val start = 20 + index / 3 * 4
      val end = if (index == 30) start else start + 2
      MarkerEntry(SnapshotMarkerReference.create(marker, true), start, end,
                  MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false),
                  flavorFlags = (index % 2).toByte(), measure = if (start == end) index + 1 else 0)
    }
    test(entries)
  }
}
