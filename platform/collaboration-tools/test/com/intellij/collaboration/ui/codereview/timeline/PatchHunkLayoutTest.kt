// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.timeline

import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch
import com.intellij.openapi.vcs.changes.patch.tool.PatchChangeBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [PatchHunkLayout.of] replaces the layout that [PatchChangeBuilder] makes from the applied split hunks, so both must agree.
 */
class PatchHunkLayoutTest {
  @Test
  fun `a deletion`() = assertSameAsPatchChangeBuilder(hunk(10, 13, 10, 12, " a", "-b", " c"))

  @Test
  fun `an insertion`() = assertSameAsPatchChangeBuilder(hunk(10, 12, 10, 13, " a", "+b", " c"))

  @Test
  fun `a modification`() = assertSameAsPatchChangeBuilder(hunk(0, 4, 0, 4, " a", "-b", "-c", "+B", "+C", " d"))

  @Test
  fun `two blocks with context between them`() =
    assertSameAsPatchChangeBuilder(hunk(5, 12, 5, 12, " a", "-b", "+B", " c", " d", " e", "-f", "+F", " g"))

  @Test
  fun `an insertion before a deletion is one block`() =
    assertSameAsPatchChangeBuilder(hunk(3, 6, 3, 6, " a", "+b", "-c", " d"))

  @Test
  fun `alternating deletions and insertions are one block`() =
    assertSameAsPatchChangeBuilder(hunk(2, 5, 2, 5, " a", "-b", "+B", "-c", "+C", " d"))

  @Test
  fun `a change at the start without context`() = assertSameAsPatchChangeBuilder(hunk(0, 2, 0, 2, "-a", "+A", " b"))

  @Test
  fun `a change at the end without context`() = assertSameAsPatchChangeBuilder(hunk(7, 9, 7, 9, " a", "-b", "+B"))

  private fun assertSameAsPatchChangeBuilder(hunk: PatchHunk) {
    val expected = PatchChangeBuilder().buildFromApplied(GenericPatchApplier.SplitHunk.read(hunk).map {
      AppliedTextPatch.AppliedSplitPatchHunk(it, -1, -1, AppliedTextPatch.HunkStatus.NOT_APPLIED)
    })
    val actual = PatchHunkLayout.of(hunk)

    assertThat(actual.text).isEqualTo(expected.patchContent.toString())
    assertThat(actual.blocks).isEqualTo(expected.hunks.map { PatchHunkLayout.Block(it.patchDeletionRange, it.patchInsertionRange) })

    val lineCount = actual.text.count { it == '\n' }
    val expectedBefore = expected.lineConvertor1.createConvertor()
    val expectedAfter = expected.lineConvertor2.createConvertor()
    for (line in 0..lineCount) {
      assertThat(actual.lineConvertorBefore.applyAsInt(line)).describedAs("the line before for $line")
        .isEqualTo(expectedBefore.applyAsInt(line))
      assertThat(actual.lineConvertorAfter.applyAsInt(line)).describedAs("the line after for $line")
        .isEqualTo(expectedAfter.applyAsInt(line))
    }
  }

  private fun hunk(startBefore: Int, endBefore: Int, startAfter: Int, endAfter: Int, vararg lines: String): PatchHunk =
    PatchHunk(startBefore, endBefore, startAfter, endAfter).apply {
      for (line in lines) {
        val type = when (line[0]) {
          '+' -> PatchLine.Type.ADD
          '-' -> PatchLine.Type.REMOVE
          else -> PatchLine.Type.CONTEXT
        }
        addLine(PatchLine(type, line.substring(1)))
      }
    }
}
