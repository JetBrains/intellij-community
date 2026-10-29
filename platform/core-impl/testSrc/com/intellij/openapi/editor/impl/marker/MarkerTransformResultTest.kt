// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.RangeMarkersImpl
import com.intellij.openapi.util.TextRangeScalarUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MarkerTransformResultTest {
  @Test
  fun `valid results preserve empty and maximum offsets`() {
    for ([start, end] in listOf(0 to 0, 2 to 4, 0 to Int.MAX_VALUE, Int.MAX_VALUE to Int.MAX_VALUE)) {
      val result = MarkerTransformResult(start, end)
      assertTrue(result.isValid)
      assertNull(result.errorReason)
      assertEquals(start, result.startOffset)
      assertEquals(end, result.endOffset)
      assertEquals(TextRangeScalarUtil.toScalarRange(start, end), result.range)
    }
  }

  @Test
  fun `invalid offsets are rejected`() {
    assertThrows(IllegalArgumentException::class.java) { MarkerTransformResult(-1, 0) }
    assertThrows(IllegalArgumentException::class.java) { MarkerTransformResult(2, 1) }
  }

  @Test
  fun `roots retain input offsets and predefined invalidation reasons`() {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val document = DocumentImpl("abcdef", true)
      val marker = document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
      val before = document.core.snapshot().text()
      val patch = DocumentPatch.simple(2, 3, "x", newModStamp = 1, clearLineFlags = false)
      val after = before.replace(patch.startOffset(), patch.endOffset(), patch.newFragment())
      for (empty in listOf(PMarkerRootImpl.empty(), PMarkerRoot.empty()).distinctBy { it.javaClass }) {
        for (error in MarkerTransformError.entries) {
          val failure = MarkerTransformResult(error)
          assertFalse(failure.isValid)
          val handle = empty.createHandle(marker, true)
          val root = empty.insert(handle, 2, 4, spec = 0, flavorFlags = 0, policy = MarkerPolicy { _, _, _, _ -> failure })
          val invalidated = ArrayList<SnapshotMarkerReference<*>>()
          val affected = ArrayList<SnapshotMarkerReference<*>>()
          val edited = root.applyPatch(patch, before, after, { invalidated.add(it) }, { affected.add(it) })

          assertEquals(PMarkerResolution.invalid(TextRangeScalarUtil.toScalarRange(2, 4)), edited.resolve(handle))
          assertEquals(error.message, edited.invalidationReason(handle))
          assertEquals(listOf(handle), invalidated)
          assertTrue(affected.isEmpty())
          assertTrue(root.isValid(handle))
          assertNull(root.invalidationReason(handle))
        }
      }
    }
  }

  @Test
  fun `roots apply valid offsets and preserve metadata`() {
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException> {
      val document = DocumentImpl("abcdef", true)
      val marker = document.createRangeMarker(2, 4) as SnapshotRangeMarkerImpl
      val before = document.core.snapshot().text()
      val patch = DocumentPatch.simple(2, 3, "x", newModStamp = 1, clearLineFlags = false)
      val after = before.replace(patch.startOffset(), patch.endOffset(), patch.newFragment())
      val policy = MarkerPolicy { _, _, _, _ -> MarkerTransformResult(1, 5) }
      val spec = MarkerSpec.create(isGreedyToLeft = true, isGreedyToRight = false)
      for (empty in listOf(PMarkerRootImpl.empty(), PMarkerRoot.empty()).distinctBy { it.javaClass }) {
        val handle = empty.createHandle(marker, true)
        val root = empty.insert(handle, 2, 4, spec, flavorFlags = 3, policy = policy)
        val edited = root.applyPatch(patch, before, after)
        assertEquals(TextRangeScalarUtil.toScalarRange(1, 5), edited.resolve(handle))
        assertNull(edited.invalidationReason(handle))
        val entry = edited.overlappingIterator(1, 5, 0).next()
        assertSame(handle, entry.handle)
        assertSame(policy, entry.policy)
        assertEquals(spec, entry.spec)
        assertEquals(3.toByte(), entry.flavorFlags)
        assertEquals(TextRangeScalarUtil.toScalarRange(2, 4), root.resolve(handle))
      }
    }
  }
}
