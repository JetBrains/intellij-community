// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import com.intellij.diff.tools.util.BaseSyncScrollable
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import org.jetbrains.annotations.ApiStatus

/**
 * Transfers a line of one side of a side-by-side diff onto the opposite side, the way
 * [com.intellij.diff.tools.simple.SimpleDiffViewer] does.
 *
 * That transfer is fully determined by the boundaries of the changed blocks and by the line counts of the two documents, so it
 * can be replayed away from the viewer that computed the diff - which is what the split mode frontend does with the blocks the
 * backend sends it.
 *
 * @param changes the changed blocks, in the order [com.intellij.diff.tools.simple.SimpleDiffViewer.getLineMappingRanges]
 *                returns them, with [Range.start1]/[Range.end1] on the left side and [Range.start2]/[Range.end2] on the right
 * @param leftLineCount [com.intellij.diff.util.DiffUtil.getLineCount] of the left document the blocks were computed from
 * @param rightLineCount [com.intellij.diff.util.DiffUtil.getLineCount] of the right document the blocks were computed from
 */
@ApiStatus.Internal
class FrontendSideBySideDiffMapping(
  private val changes: List<Range>,
  private val leftLineCount: Int,
  private val rightLineCount: Int,
) {
  private val scrollable = MyScrollable()

  /**
   * Transfers the [line] of the [side] document onto the opposite side.
   *
   * The result is approximate: a line inside a changed block is transferred to the closest line of the matching block on the
   * opposite side, and a line past the end of the [side] document is transferred past the end of the opposite one, so it may
   * be the line count of the opposite document rather than one of its lines.
   *
   * @param line a non-negative line of the [side] document
   */
  fun transferLine(side: Side, line: Int): Int {
    require(line >= 0) { "Invalid line number: $line" }
    return scrollable.transfer(side, line)
  }

  /**
   * Replays [com.intellij.diff.tools.simple.SimpleDiffViewer]'s own scrollable, including its shortcut for a diff without
   * changed blocks, so that the two transfer every line to the same place. It never takes part in scrolling.
   */
  private inner class MyScrollable : BaseSyncScrollable() {
    /** Read only by a sync scroll support, which this scrollable is never installed into. */
    override fun isSyncScrollEnabled(): Boolean = false

    override fun getRange(baseSide: Side, line: Int): Range {
      if (changes.isEmpty()) return idRange(line)
      return super.getRange(baseSide, line)
    }

    override fun processHelper(helper: ScrollHelper) {
      if (!helper.process(0, 0)) return
      for (change in changes) {
        if (!helper.process(change.start1, change.start2)) return
        if (!helper.process(change.end1, change.end2)) return
      }
      helper.process(leftLineCount, rightLineCount)
    }
  }
}
