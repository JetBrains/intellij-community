/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.diff.comparison

import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.fragments.MergeWordFragmentImpl
import com.intellij.diff.util.*
import com.intellij.diff.util.Side.LEFT
import com.intellij.diff.util.Side.RIGHT
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.util.text.MergingCharSequence

object MergeResolveUtil {
  @JvmStatic
  fun tryResolve(leftText: CharSequence, baseText: CharSequence, rightText: CharSequence): CharSequence? {
    try {
      val resolved = trySimpleResolve(leftText, baseText, rightText, ComparisonPolicy.DEFAULT)
      if (resolved != null) return resolved

      return trySimpleResolve(leftText, baseText, rightText, ComparisonPolicy.IGNORE_WHITESPACES)
    }
    catch (e: DiffTooBigException) {
      return null
    }
  }

  /*
   * Here we assume, that resolve results are explicitly verified by user and can be safely undone.
   * Thus we trade higher chances of incorrect resolve for higher chances of correct resolve.
   *
   * We're making an assertion, that "A-X-B" and "B-X-A" conflicts should produce equal results.
   * This leads us to conclusion, that insertion-insertion conflicts can't be possibly resolved (if inserted fragments are different),
   * because we don't know the right order of inserted chunks (and sorting them alphabetically or by length makes no sense).
   *
   * deleted-inserted conflicts can be resolved by applying both of them.
   * deleted-deleted conflicts can be resolved by merging deleted intervals.
   * modifications can be considered as "insertion + deletion" and resolved accordingly.
   */
  @JvmStatic
  fun tryGreedyResolve(leftText: CharSequence, baseText: CharSequence, rightText: CharSequence): CharSequence? {
    try {
      val resolved = tryGreedyResolve(leftText, baseText, rightText, ComparisonPolicy.DEFAULT)
      if (resolved != null) return resolved

      return tryGreedyResolve(leftText, baseText, rightText, ComparisonPolicy.IGNORE_WHITESPACES)
    }
    catch (e: DiffTooBigException) {
      return null
    }
  }
}

private fun trySimpleResolve(leftText: CharSequence, baseText: CharSequence, rightText: CharSequence,
                             policy: ComparisonPolicy): CharSequence? {
  return SimpleHelper(leftText, baseText, rightText).execute(policy)
}

private fun tryGreedyResolve(leftText: CharSequence, baseText: CharSequence, rightText: CharSequence,
                             policy: ComparisonPolicy): CharSequence? {
  return GreedyHelper(leftText, baseText, rightText).execute(policy)
}


private class SimpleHelper(val leftText: CharSequence, val baseText: CharSequence, val rightText: CharSequence) {
  private val newContent = StringBuilder()

  private var last1 = 0
  private var last2 = 0
  private var last3 = 0

  private val texts = listOf(leftText, baseText, rightText)

  fun execute(policy: ComparisonPolicy): CharSequence? {
    val changes = ByWord.compare(leftText, baseText, rightText, policy, DumbProgressIndicator.INSTANCE)

    for (fragment in changes) {
      val baseRange = nextMergeRange(fragment.getStartOffset(ThreeSide.LEFT),
                                     fragment.getStartOffset(ThreeSide.BASE),
                                     fragment.getStartOffset(ThreeSide.RIGHT))
      appendBase(baseRange)

      val conflictRange = nextMergeRange(fragment.getEndOffset(ThreeSide.LEFT),
                                         fragment.getEndOffset(ThreeSide.BASE),
                                         fragment.getEndOffset(ThreeSide.RIGHT))
      if (!appendConflict(conflictRange, policy)) return null
    }

    val trailingRange = nextMergeRange(leftText.length, baseText.length, rightText.length)
    appendBase(trailingRange)

    return newContent.toString()
  }

  private fun nextMergeRange(end1: Int, end2: Int, end3: Int): MergeRange {
    val range = MergeRange(last1, end1, last2, end2, last3, end3)
    last1 = end1
    last2 = end2
    last3 = end3
    return range
  }

  private fun appendBase(range: MergeRange) {
    if (range.isEmpty) return

    val policy = ComparisonPolicy.DEFAULT

    if (isUnchangedRange(range, policy)) {
      append(range, ThreeSide.BASE)
    }
    else {
      val type = getConflictType(range, policy)
      if (type.isChange(Side.LEFT)) {
        append(range, ThreeSide.LEFT)
      }
      else if (type.isChange(Side.RIGHT)) {
        append(range, ThreeSide.RIGHT)
      }
      else {
        append(range, ThreeSide.BASE)
      }
    }
  }

  private fun appendConflict(range: MergeRange, policy: ComparisonPolicy): Boolean {
    val type = getConflictType(range, policy)
    if (type.diffType == TextDiffType.CONFLICT) return false

    if (type.isChange(Side.LEFT)) {
      append(range, ThreeSide.LEFT)
    }
    else {
      append(range, ThreeSide.RIGHT)
    }

    return true
  }

  private fun append(range: MergeRange, side: ThreeSide) {
    when (side) {
      ThreeSide.LEFT -> newContent.append(leftText, range.start1, range.end1)
      ThreeSide.BASE -> newContent.append(baseText, range.start2, range.end2)
      ThreeSide.RIGHT -> newContent.append(rightText, range.start3, range.end3)
    }
  }

  private fun getConflictType(range: MergeRange, policy: ComparisonPolicy): MergeConflictType {
    return DiffUtil.getWordMergeType(MergeWordFragmentImpl(range), texts, policy)
  }

  private fun isUnchangedRange(range: MergeRange, policy: ComparisonPolicy): Boolean {
    return DiffUtil.compareWordMergeContents(MergeWordFragmentImpl(range), texts, policy, ThreeSide.BASE, ThreeSide.LEFT) &&
           DiffUtil.compareWordMergeContents(MergeWordFragmentImpl(range), texts, policy, ThreeSide.BASE, ThreeSide.RIGHT)
  }
}


private class GreedyHelper(val leftText: CharSequence, val baseText: CharSequence, val rightText: CharSequence) {
  private val newContent = StringBuilder()

  private var lastBaseOffset = 0
  private var index1 = 0
  private var index2 = 0

  fun execute(policy: ComparisonPolicy): CharSequence? {
    val fragments1 = ByWord.compare(baseText, leftText, policy, DumbProgressIndicator.INSTANCE)
    val fragments2 = ByWord.compare(baseText, rightText, policy, DumbProgressIndicator.INSTANCE)

    while (true) {
      val changeStart1 = fragments1.getOrNull(index1)?.startOffset1 ?: -1
      val changeStart2 = fragments2.getOrNull(index2)?.startOffset1 ?: -1

      if (changeStart1 == -1 && changeStart2 == -1) {
        // no more changes left
        appendBase(baseText.length)
        break
      }

      // skip till the next block of changes
      if (changeStart1 != -1 && changeStart2 != -1) {
        appendBase(Math.min(changeStart1, changeStart2))
      }
      else if (changeStart1 != -1) {
        appendBase(changeStart1)
      }
      else {
        appendBase(changeStart2)
      }


      // collect next block of changes, that intersect one another.
      var baseOffsetEnd = lastBaseOffset
      var end1 = index1
      var end2 = index2

      while (true) {
        val next1 = fragments1.getOrNull(end1)
        val next2 = fragments2.getOrNull(end2)

        if (next1 != null && next1.startOffset1 <= baseOffsetEnd) {
          baseOffsetEnd = Math.max(baseOffsetEnd, next1.endOffset1)
          end1++
          continue
        }
        if (next2 != null && next2.startOffset1 <= baseOffsetEnd) {
          baseOffsetEnd = Math.max(baseOffsetEnd, next2.endOffset1)
          end2++
          continue
        }

        break
      }
      assert(index1 != end1 || index2 != end2)

      val inserted1 = getInsertedContent(fragments1, index1, end1, LEFT)
      val inserted2 = getInsertedContent(fragments2, index2, end2, RIGHT)
      index1 = end1
      index2 = end2


      // merge and apply deletions
      lastBaseOffset = baseOffsetEnd


      // merge and apply non-conflicted insertions
      if (inserted1.isEmpty() && inserted2.isEmpty()) continue
      if (inserted2.isEmpty()) {
        newContent.append(inserted1)
        continue
      }
      if (inserted1.isEmpty()) {
        newContent.append(inserted2)
        continue
      }

      if (ComparisonUtil.isEquals(inserted1, inserted2, policy)) {
        val inserted = if (inserted1.length <= inserted2.length) inserted1 else inserted2
        newContent.append(inserted)
        continue
      }

      // we faced conflicting insertions - resolve failed
      return null;
    }

    return newContent
  }

  private fun appendBase(endOffset: Int) {
    if (lastBaseOffset == endOffset) return
    newContent.append(baseText.subSequence(lastBaseOffset, endOffset))
    lastBaseOffset = endOffset
  }

  private fun getInsertedContent(fragments: List<DiffFragment>, start: Int, end: Int, side: Side): CharSequence {
    val text = side.select(leftText, rightText)!!

    val empty: CharSequence = ""
    return fragments.subList(start, end).fold(empty, { prefix, fragment ->
      MergingCharSequence(prefix, text.subSequence(fragment.startOffset2, fragment.endOffset2))
    })
  }
}
