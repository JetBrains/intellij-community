// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.diff.DiffContext
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineCol
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [FrontendSideBySideDiffMapping] replays the line transfer of [SimpleDiffViewer] from the changed blocks alone, so most of these
 * tests are differential: they build a real viewer, hand it the blocks the split mode backend would ship, and assert that the two
 * transfer every line of both documents to the same place. They pin nothing about the transfer itself, only that the copy has not
 * drifted from the original.
 *
 * The few tests that do assert absolute line numbers cover what a viewer cannot easily be made to produce, and keep the
 * differential sweeps from passing because both sides became the identity.
 */
@TestApplication
internal class FrontendSideBySideDiffMappingTest {
  @Test
  fun `equal contents`() = assertAgreesWithViewer("a_b_c", "a_b_c", expectedBlocks = 0)

  @Test
  fun `inserted line`() = assertAgreesWithViewer("a_b_c", "a_x_b_c", expectedBlocks = 1)

  @Test
  fun `deleted line`() = assertAgreesWithViewer("a_x_b_c", "a_b_c", expectedBlocks = 1)

  @Test
  fun `block with unequal side lengths`() = assertAgreesWithViewer("a_b_c_d", "a_x_y_z_d", expectedBlocks = 1)

  @Test
  fun `block at the first line`() = assertAgreesWithViewer("x_b_c", "y_b_c", expectedBlocks = 1)

  @Test
  fun `block at the last line`() = assertAgreesWithViewer("a_b_x", "a_b_y", expectedBlocks = 1)

  @Test
  fun `two blocks`() = assertAgreesWithViewer("1_2_3_4_5_6_7", "1_x_3_4_5_y_7", expectedBlocks = 2)

  @Test
  fun `whole file changed with unequal line counts`() = assertAgreesWithViewer("x_y", "1_2_3_4_5", expectedBlocks = 1)

  @Test
  fun `left side empty`() = assertAgreesWithViewer("", "a_b", expectedBlocks = 1)

  @Test
  fun `right side empty`() = assertAgreesWithViewer("a_b", "", expectedBlocks = 1)

  @Test
  fun `both sides empty`() = assertAgreesWithViewer("", "", expectedBlocks = 0)

  @Test
  fun `trailing newline only`() = assertAgreesWithViewer("a_b", "a_b_", expectedBlocks = 1)

  @Test
  fun `a diff without blocks is the identity even when the documents differ in length`() {
    // this is what SimpleDiffViewer's own short-circuit for an empty change list does, and the anchor walk would not:
    // it would clamp LEFT line 4 into the single (0, 5) - (0, 3) span and answer 3
    val mapping = FrontendSideBySideDiffMapping(emptyList(), leftLineCount = 5, rightLineCount = 3)
    assertEquals(4, mapping.transferLine(Side.LEFT, 4))
    assertEquals(2, mapping.transferLine(Side.RIGHT, 2))
    assertEquals(7, mapping.transferLine(Side.LEFT, 7))
  }

  @Test
  fun `a line inside a block is clamped to the end of the matching block`() {
    // one block covering all 5 lines of the left document and both lines of the right one
    val mapping = FrontendSideBySideDiffMapping(listOf(Range(0, 5, 0, 2)), leftLineCount = 5, rightLineCount = 2)
    assertEquals(0, mapping.transferLine(Side.LEFT, 0))
    assertEquals(1, mapping.transferLine(Side.LEFT, 1))
    assertEquals(2, mapping.transferLine(Side.LEFT, 2))
    assertEquals(2, mapping.transferLine(Side.LEFT, 3))
    assertEquals(2, mapping.transferLine(Side.LEFT, 5))
    // past the end of the left document the transfer extrapolates, so it may answer past the end of the right one too
    assertEquals(3, mapping.transferLine(Side.LEFT, 6))
  }

  @Test
  fun `a negative line is rejected`() {
    val mapping = FrontendSideBySideDiffMapping(emptyList(), leftLineCount = 1, rightLineCount = 1)
    assertThrows<IllegalArgumentException> { mapping.transferLine(Side.LEFT, -1) }
  }
}

/**
 * Asserts that a mapping built the way the split mode backend builds one - from [SimpleDiffViewer.getLineMappingRanges] and the
 * line counts of the two documents - transfers every line of either document exactly where the viewer itself transfers it.
 *
 * `_` stands for a line break in [left] and [right], as it does in `DiffTestCase`. [expectedBlocks] is how many changed blocks the
 * diff of the two is expected to have; it keeps the sweep from agreeing only because both sides became the identity.
 */
private fun assertAgreesWithViewer(left: String, right: String, expectedBlocks: Int) {
  timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    writeIntentReadAction {
      withViewer(left, right) { viewer ->
        assertEquals(expectedBlocks, viewer.lineMappingRanges.size, "changed blocks: ${viewer.lineMappingRanges}")
        val mapping = FrontendSideBySideDiffMapping(
          changes = viewer.lineMappingRanges,
          leftLineCount = DiffUtil.getLineCount(viewer.editor1.document),
          rightLineCount = DiffUtil.getLineCount(viewer.editor2.document),
        )
        for (side in Side.entries) {
          // a few lines past the end of the document as well, where both sides extrapolate
          for (line in 0..DiffUtil.getLineCount(viewer.getEditor(side).document) + 2) {
            assertEquals(viewer.transferPosition(side, LineCol(line, 0)).line, mapping.transferLine(side, line),
                         "$side line $line, blocks ${viewer.lineMappingRanges}")
          }
        }
      }
    }
  }
}

private fun withViewer(left: String, right: String, block: (SimpleDiffViewer) -> Unit) {
  val contentFactory = DiffContentFactoryImpl()
  val request = SimpleDiffRequest(null,
                                  contentFactory.create(parseSource(left)),
                                  contentFactory.create(parseSource(right)),
                                  null, null)
  val context = TestDiffContext()
  // own settings, so that the persisted application ones cannot change which blocks the diff has
  context.putUserData(TextDiffSettings.KEY, TextDiffSettings())

  val viewer = SimpleDiffViewer(context, request)
  try {
    // installs the sync scroll support that transferPosition delegates to; without it the transfer is the identity
    viewer.init()
    // on the EDT a write lock makes DiffViewerBase.forceRediffSynchronously() true, so this rediff runs and finishes inline
    runWriteAction { viewer.rediff(true) }
    assertFalse(viewer.hasPendingRediff(), "the rediff did not finish synchronously")
    block(viewer)
  }
  finally {
    Disposer.dispose(viewer)
  }
}

private fun parseSource(text: String): String = text.replace('_', '\n')

private class TestDiffContext : DiffContext() {
  override fun getProject(): Project? = null
  override fun isWindowFocused(): Boolean = false
  override fun isFocusedInWindow(): Boolean = false
  override fun requestFocusInWindow() = Unit
}
