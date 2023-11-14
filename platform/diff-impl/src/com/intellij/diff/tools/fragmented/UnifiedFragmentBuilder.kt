/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.tools.fragmented

import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

abstract class UnifiedFragmentBuilder(protected val document1: Document,
                                      protected val document2: Document,
                                      protected val masterSide: Side) {
  protected val textBuilder = StringBuilder()
  protected val changes = mutableListOf<UnifiedDiffChange>()
  protected val ranges = mutableListOf<HighlightRange>()
  protected val convertorBuilder1 = LineNumberConvertor.Builder()
  protected val convertorBuilder2 = LineNumberConvertor.Builder()
  protected val changedLines = mutableListOf<LineRange>()

  private var lastProcessedLine1 = -1
  private var lastProcessedLine2 = -1
  private var totalLines = 0

  protected fun finishDocuments() {
    processEquals(DiffUtil.getLineCount(document1) - 1, DiffUtil.getLineCount(document2) - 1)
  }

  protected fun processEquals(endLine1: Int, endLine2: Int) {
    val startLine1 = lastProcessedLine1 + 1
    val startLine2 = lastProcessedLine2 + 1

    appendTextMaster(startLine1, startLine2, endLine1, endLine2)
  }

  protected fun processChanged(fragment: Range): BlockLineRange {
    processEquals(fragment.start1 - 1, fragment.start2 - 1)

    val startLine1 = fragment.start1
    val endLine1 = fragment.end1 - 1
    val lines1 = endLine1 - startLine1

    val startLine2 = fragment.start2
    val endLine2 = fragment.end2 - 1
    val lines2 = endLine2 - startLine2

    val linesBefore = totalLines

    if (lines1 >= 0) {
      val startOffset1 = document1.getLineStartOffset(startLine1)
      val endOffset1 = document1.getLineEndOffset(endLine1)
      appendText(Side.LEFT, startOffset1, endOffset1, lines1, lines2, startLine1, -1)
    }

    val linesBetween = totalLines

    if (lines2 >= 0) {
      val startOffset2 = document2.getLineStartOffset(startLine2)
      val endOffset2 = document2.getLineEndOffset(endLine2)
      appendText(Side.RIGHT, startOffset2, endOffset2, lines2, lines2, -1, startLine2)
    }

    val linesAfter = totalLines

    lastProcessedLine1 = endLine1
    lastProcessedLine2 = endLine2

    return BlockLineRange(linesBefore, linesBetween, linesAfter)
  }

  protected fun reportChange(change: UnifiedDiffChange) {
    changes.add(change)
    if (!change.isSkipped) {
      changedLines.add(LineRange(change.line1, change.line2))
    }
  }

  private fun appendTextMaster(startLine1: Int, startLine2: Int, endLine1: Int, endLine2: Int) {
    // The slave-side line matching might be incomplete for non-fair line fragments (@see FairDiffIterable)
    // If it ever became an issue, it could be fixed by explicit fair by-line comparing of "equal" regions

    val lines1 = endLine1 - startLine1
    val lines2 = endLine2 - startLine2
    if (masterSide.select(lines1, lines2) >= 0) {
      val startOffset = if (masterSide.isLeft) document1.getLineStartOffset(startLine1) else document2.getLineStartOffset(startLine2)
      val endOffset = if (masterSide.isLeft) document1.getLineEndOffset(endLine1) else document2.getLineEndOffset(endLine2)

      appendText(masterSide, startOffset, endOffset, lines1, lines2, startLine1, startLine2)
    }
  }

  private fun appendText(side: Side, offset1: Int, offset2: Int, lines1: Int, lines2: Int, startLine1: Int, startLine2: Int) {
    val lines = side.select(lines1, lines2)
    val notEmpty = lines >= 0

    val appendix = if (notEmpty) 1 else 0
    if (startLine1 != -1) {
      convertorBuilder1.put(totalLines, startLine1, lines + appendix, lines1 + appendix)
    }
    if (startLine2 != -1) {
      convertorBuilder2.put(totalLines, startLine2, lines + appendix, lines2 + appendix)
    }

    if (notEmpty) {
      val document = side.selectNotNull(document1, document2)

      val newline = if (document.textLength > offset2 + 1) 1 else 0
      val base = TextRange(textBuilder.length, textBuilder.length + offset2 - offset1 + newline)
      val changed = TextRange(offset1, offset2 + newline)
      ranges.add(HighlightRange(side, base, changed))

      textBuilder.append(document.charsSequence.subSequence(offset1, offset2))
      textBuilder.append('\n')

      totalLines += lines + 1
    }
  }

  protected class BlockLineRange(val blockStart: Int, val insertedStart: Int, val blockEnd: Int)
}

class SimpleUnifiedFragmentBuilder(document1: Document,
                                   document2: Document,
                                   masterSide: Side
) : UnifiedFragmentBuilder(document1, document2, masterSide) {

  fun exec(fragments: List<LineFragment>): UnifiedDiffState {
    for (fragment in fragments) {
      val blockLineRange = processChanged(fragment.asLineRange())

      val change = UnifiedDiffChange(blockLineRange.blockStart, blockLineRange.insertedStart, blockLineRange.blockEnd,
                                     fragment)
      reportChange(change)
    }
    finishDocuments()

    return UnifiedDiffState(masterSide, textBuilder, changes, ranges, convertorBuilder1.build(), convertorBuilder2.build(), changedLines)
  }
}

class UnifiedDiffState(
  val masterSide: Side,
  val text: CharSequence,
  val changes: List<UnifiedDiffChange>,
  val ranges: List<HighlightRange>,
  val convertor1: LineNumberConvertor,
  val convertor2: LineNumberConvertor,
  val changedLines: List<LineRange>
)

fun LineFragment.asLineRange(): Range {
  return Range(startLine1, endLine1, startLine2, endLine2)
}