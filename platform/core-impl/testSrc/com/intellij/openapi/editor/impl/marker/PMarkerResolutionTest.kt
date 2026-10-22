// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRangeScalarUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PMarkerResolutionTest {
  @Test
  fun `states preserve empty and maximum offsets`() {
    for (range in listOf(TextRange.EMPTY_RANGE, TextRange(2, 4), TextRange(0, Int.MAX_VALUE), TextRange(Int.MAX_VALUE, Int.MAX_VALUE))) {
      val valid = PMarkerResolution.valid(range.startOffset, range.endOffset)
      val invalid = PMarkerResolution.invalid(range.startOffset, range.endOffset)
      assertEquals(TextRangeScalarUtil.toScalarRange(range), valid)
      assertTrue(PMarkerResolution.isValid(valid))
      assertFalse(PMarkerResolution.isValid(invalid))
      for (resolution in longArrayOf(valid, invalid)) {
        assertNotEquals(PMarkerResolution.UNRESOLVED, resolution)
        assertNotEquals(PMarkerResolution.ABSENT, resolution)
        assertEquals(range.startOffset, PMarkerResolution.startOffset(resolution))
        assertEquals(range.endOffset, PMarkerResolution.endOffset(resolution))
        assertEquals(range, PMarkerResolution.toTextRange(resolution))
        assertEquals(resolution == valid, PMarkerResolution.isValid(resolution))
        assertEquals(resolution == invalid, !PMarkerResolution.isValid(resolution))
        assertFalse(PMarkerResolution.isAbsent(resolution))
      }
    }
  }

  @Test
  fun `unresolved is not a marker state`() {
    assertFalse(PMarkerResolution.isValid(PMarkerResolution.UNRESOLVED))
    assertFalse(PMarkerResolution.isAbsent(PMarkerResolution.UNRESOLVED))
  }

  @Test
  fun `absent is a distinct sentinel without offsets`() {
    assertEquals(-2L, PMarkerResolution.ABSENT)
    assertNotEquals(PMarkerResolution.UNRESOLVED, PMarkerResolution.ABSENT)
    assertTrue(PMarkerResolution.isAbsent(PMarkerResolution.ABSENT))
    assertFalse(PMarkerResolution.isValid(PMarkerResolution.ABSENT))
    assertEquals(PMarkerResolution.ABSENT, PMarkerRootImpl.empty().resolve(1))
    assertFalse(PMarkerRootImpl.empty().isValid(1))
  }

  @Test
  fun `invalid offsets are rejected`() {
    for ((start, end) in listOf(-1 to 0, 2 to 1)) {
      assertThrows(IllegalArgumentException::class.java) { PMarkerResolution.valid(start, end) }
      assertThrows(IllegalArgumentException::class.java) { PMarkerResolution.invalid(start, end) }
    }
  }

  @Test
  fun `removed markers stay absent after a merge`() {
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val root = PMarkerRootImpl.empty().insert(1, 2, 4, spec, flavorFlags = 0)
    val removed = root.remove(1)

    assertTrue(root.isValid(1))
    assertFalse(removed.isValid(1))
    assertEquals(PMarkerResolution.ABSENT, removed.resolve(1))
    assertEquals(PMarkerResolution.valid(2, 4), root.resolve(1))
    assertSame(removed, removed.remove(1))
    assertEquals(PMarkerResolution.ABSENT, removed.mergeValidMarkersFrom(root).resolve(1))
    assertEquals(PMarkerResolution.valid(3, 6), removed.insert(1, 3, 6, spec, flavorFlags = 0).resolve(1))
  }

  @Test
  fun `invalidation reasons remain in their roots`() {
    val document = DocumentImpl("abcdef", true)
    val reason = "Invalidated by test marker policy"
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val policy = MarkerPolicy { entry, _, _, _ ->
      MarkerTransformResult(entry, reason)
    }
    val root = PMarkerRootImpl.empty().insert(1, 2, 4, spec, flavorFlags = 0, policy = policy)
    val before = document.core.snapshot().text()
    val patch = DocumentPatch.simple(2, 3, "x", newModStamp = 1, clearLineFlags = false)
    val after = patch.ops().fold(before) { current, op -> current.applyOp(op) }
    val edited = root.applyPatch(patch, before, after)

    assertTrue(root.isValid(1))
    assertFalse(edited.isValid(1))
    assertFalse(edited.isValid(2))
    assertEquals(PMarkerResolution.invalid(2, 4), edited.resolve(1))
    assertEquals(reason, edited.invalidationReason(1))
    assertNull(root.invalidationReason(1))
    assertNull(edited.invalidationReason(2))
    val removed = edited.remove(1)
    assertNull(removed.invalidationReason(1))
    assertEquals(PMarkerResolution.ABSENT, removed.resolve(1))
    assertEquals(PMarkerResolution.invalid(2, 4), edited.resolve(1))
  }
}
