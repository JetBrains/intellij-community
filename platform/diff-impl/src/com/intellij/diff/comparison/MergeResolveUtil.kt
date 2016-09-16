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
import com.intellij.diff.util.Side
import com.intellij.diff.util.Side.LEFT
import com.intellij.diff.util.Side.RIGHT
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.util.text.MergingCharSequence

object MergeResolveUtil {
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
  fun tryResolveConflict(leftText: CharSequence, baseText: CharSequence, rightText: CharSequence): CharSequence? {
    try {
      val resolved = Helper(leftText, baseText, rightText).execute(ComparisonPolicy.DEFAULT)
      if (resolved != null) return resolved

      return Helper(leftText, baseText, rightText).execute(ComparisonPolicy.IGNORE_WHITESPACES)
    }
    catch (e: DiffTooBigException) {
      return null
    }
  }
}

private class Helper(val leftText: CharSequence, val baseText: CharSequence, val rightText: CharSequence) {
  val newContent = StringBuilder()

  var lastBaseOffset = 0
  var index1 = 0
  var index2 = 0

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
