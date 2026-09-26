package com.intellij.platform.lsp.unit

import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspCachedHighlighting
import com.intellij.platform.lsp.impl.features.highlightingCommon.PendingEdit
import com.intellij.platform.lsp.impl.features.highlightingCommon.applyPendingEdits
import org.junit.Assert.assertEquals
import org.junit.Test

class LspApplyPendingEditsTest {

  @Test
  fun `edit before range shifts it`() {
    // Insert 3 chars at offset 0, range [5,10) should shift to [8,13)
    val result = applyEdits(
      ranges = listOf(TextRange(5, 10)),
      edits = listOf(PendingEdit(offset = 0, oldLength = 0, newLength = 3)),
    )
    assertEquals(listOf(TextRange(8, 13)), result)
  }

  @Test
  fun `edit after range keeps it`() {
    val result = applyEdits(
      ranges = listOf(TextRange(5, 10)),
      edits = listOf(PendingEdit(offset = 15, oldLength = 0, newLength = 3)),
    )
    assertEquals(listOf(TextRange(5, 10)), result)
  }

  @Test
  fun `edit within range grows it`() {
    // Replace 1 char with 2 chars inside [5,10) — range grows to [5,11)
    val result = applyEdits(
      ranges = listOf(TextRange(5, 10)),
      edits = listOf(PendingEdit(offset = 7, oldLength = 1, newLength = 2)),
    )
    assertEquals(listOf(TextRange(5, 11)), result)
  }

  @Test
  fun `deletion within range shrinks it`() {
    // Delete 2 chars inside [5,10) — range shrinks to [5,8)
    val result = applyEdits(
      ranges = listOf(TextRange(5, 10)),
      edits = listOf(PendingEdit(offset = 6, oldLength = 2, newLength = 0)),
    )
    assertEquals(listOf(TextRange(5, 8)), result)
  }

  @Test
  fun `insertion at range start shifts it`() {
    // Insert at the start boundary — treated as "before" and shift
    val result = applyEdits(
      ranges = listOf(TextRange(5, 10)),
      edits = listOf(PendingEdit(offset = 5, oldLength = 0, newLength = 1)),
    )
    assertEquals(listOf(TextRange(6, 11)), result)
  }

  @Test
  fun `insertion at range end keeps it`() {
    // Insert at the end boundary — treated as "after" and unchanged
    val result = applyEdits(
      ranges = listOf(TextRange(5, 10)),
      edits = listOf(PendingEdit(offset = 10, oldLength = 0, newLength = 1)),
    )
    assertEquals(listOf(TextRange(5, 10)), result)
  }

  @Test
  fun `partially overlapping edit removes range`() {
    // Replace [3,7) overlapping [5,10) — not fully contained, so removed
    val result = applyEdits(
      ranges = listOf(TextRange(5, 10)),
      edits = listOf(PendingEdit(offset = 3, oldLength = 4, newLength = 1)),
    )
    assertEquals(emptyList<TextRange>(), result)
  }

  @Test
  fun `no edits returns original ranges`() {
    val result = applyEdits(
      ranges = listOf(TextRange(5, 10), TextRange(15, 20)),
      edits = emptyList(),
    )
    assertEquals(listOf(TextRange(5, 10), TextRange(15, 20)), result)
  }

  @Test
  fun `empty ranges returns empty`() {
    val result = applyEdits(
      ranges = emptyList(),
      edits = listOf(PendingEdit(offset = 0, oldLength = 0, newLength = 5)),
    )
    assertEquals(emptyList<TextRange>(), result)
  }

  private fun applyEdits(
    ranges: List<TextRange>,
    edits: List<PendingEdit>,
  ): List<TextRange> {
    val highlightings = ranges.map { LspCachedHighlighting(it, Unit) }
    return applyPendingEdits(highlightings, edits).map { it.textRange }
  }
}