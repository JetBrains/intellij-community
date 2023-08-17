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
import com.intellij.diff.util.DiffUtil.getLineCount
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

open class UnifiedFragmentBuilder(protected val fragments: List<LineFragment>,
                                  protected val document1: Document,
                                  protected val document2: Document,
                                  protected val masterSide: Side) {
  private val textBuilder = StringBuilder()
  private val changes = mutableListOf<UnifiedDiffChange>()
  private val ranges = mutableListOf<HighlightRange>()
  private val convertorBuilder1 = LineNumberConvertor.Builder()
  private val convertorBuilder2 = LineNumberConvertor.Builder()
  private val changedLines = mutableListOf<LineRange>()

  private var lastProcessedLine1 = -1
  private var lastProcessedLine2 = -1
  private var totalLines = 0

  fun exec(): UnifiedDiffState {
    if (fragments.isEmpty()) {
      appendTextMaster(0, 0, getLineCount(document1) - 1, getLineCount(document2) - 1)
      return finish()
    }

    for (i in fragments.indices) {
      val fragment = fragments[i]
      processEquals(fragment.getStartLine1() - 1, fragment.getStartLine2() - 1)
      processChanged(fragment, i)
    }
    processEquals(getLineCount(document1) - 1, getLineCount(document2) - 1)

    return finish()
  }

  private fun finish(): UnifiedDiffState {
    return UnifiedDiffState(masterSide, textBuilder, changes, ranges, convertorBuilder1.build(), convertorBuilder2.build(), changedLines)
  }

  private fun processEquals(endLine1: Int, endLine2: Int) {
    val startLine1 = lastProcessedLine1 + 1
    val startLine2 = lastProcessedLine2 + 1

    appendTextMaster(startLine1, startLine2, endLine1, endLine2)
  }

  private fun processChanged(fragment: LineFragment, fragmentIndex: Int) {
    val startLine1 = fragment.getStartLine1()
    val endLine1 = fragment.getEndLine1() - 1
    val lines1 = endLine1 - startLine1

    val startLine2 = fragment.getStartLine2()
    val endLine2 = fragment.getEndLine2() - 1
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

    val change = createDiffChange(linesBefore, linesBetween, linesAfter, fragmentIndex)
    changes.add(change)
    if (!change.isSkipped) {
      changedLines.add(LineRange(linesBefore, linesAfter))
    }

    lastProcessedLine1 = endLine1
    lastProcessedLine2 = endLine2
  }

  protected open fun createDiffChange(blockStart: Int,
                                      insertedStart: Int,
                                      blockEnd: Int,
                                      fragmentIndex: Int): UnifiedDiffChange {
    return UnifiedDiffChange(blockStart, insertedStart, blockEnd, fragments[fragmentIndex])
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