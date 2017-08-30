/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
@file:JvmName("RangesBuilder")

package com.intellij.openapi.vcs.ex

import com.intellij.diff.comparison.ByLine
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.comparison.expand
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.vcs.ex.Range.InnerRange
import com.intellij.util.diff.FilesTooBigForDiffException
import java.util.*

@JvmOverloads
@Throws(FilesTooBigForDiffException::class)
fun createRanges(current: Document, vcs: Document, innerWhitespaceChanges: Boolean = false): List<Range> {
  return createRanges(DiffUtil.getLines(current), DiffUtil.getLines(vcs), 0, 0, innerWhitespaceChanges)
}

@Throws(FilesTooBigForDiffException::class)
fun createRanges(current: List<String>,
                 vcs: List<String>,
                 currentShift: Int,
                 vcsShift: Int,
                 innerWhitespaceChanges: Boolean): List<Range> {
  try {
    return if (innerWhitespaceChanges) {
      createRangesSmart(current, vcs, currentShift, vcsShift)
    }
    else {
      createRangesSimple(current, vcs, currentShift, vcsShift)
    }
  }
  catch (e: DiffTooBigException) {
    throw FilesTooBigForDiffException()
  }

}

@Throws(DiffTooBigException::class)
private fun createRangesSimple(current: List<String>,
                               vcs: List<String>,
                               currentShift: Int,
                               vcsShift: Int): List<Range> {
  val iterable = ByLine.compare(vcs, current, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)

  val result = ArrayList<Range>()
  for (range in iterable.iterateChanges()) {
    val vcsLine1 = vcsShift + range.start1
    val vcsLine2 = vcsShift + range.end1
    val currentLine1 = currentShift + range.start2
    val currentLine2 = currentShift + range.end2

    result.add(Range(currentLine1, currentLine2, vcsLine1, vcsLine2))
  }
  return result
}

@Throws(DiffTooBigException::class)
private fun createRangesSmart(current: List<String>,
                              vcs: List<String>,
                              currentShift: Int,
                              vcsShift: Int): List<Range> {
  val iwIterable = ByLine.compare(vcs, current, ComparisonPolicy.IGNORE_WHITESPACES, DumbProgressIndicator.INSTANCE)

  val rangeBuilder = RangeBuilder(current, vcs, currentShift, vcsShift)

  for (range in iwIterable.iterateUnchanged()) {
    val count = range.end1 - range.start1
    for (i in 0..count - 1) {
      val vcsIndex = range.start1 + i
      val currentIndex = range.start2 + i
      if (vcs[vcsIndex] == current[currentIndex]) {
        rangeBuilder.markEqual(vcsIndex, currentIndex)
      }
    }
  }

  return rangeBuilder.finish()
}

private class RangeBuilder(private val myCurrent: List<String>,
                           private val myVcs: List<String>,
                           private val myCurrentShift: Int,
                           private val myVcsShift: Int) : DiffIterableUtil.ChangeBuilderBase(myVcs.size, myCurrent.size) {

  private val myResult = ArrayList<Range>()

  fun finish(): List<Range> {
    doFinish()
    return myResult
  }

  override fun addChange(vcsStart: Int, currentStart: Int, vcsEnd: Int, currentEnd: Int) {
    val range = expand(myVcs, myCurrent, vcsStart, currentStart, vcsEnd, currentEnd)
    if (range.isEmpty) return

    val innerRanges = calcInnerRanges(range)
    val newRange = Range(range.start2 + myCurrentShift, range.end2 + myCurrentShift,
                         range.start1 + myVcsShift, range.end1 + myVcsShift, innerRanges)

    myResult.add(newRange)
  }

  private fun calcInnerRanges(blockRange: com.intellij.diff.util.Range): List<InnerRange>? {
    try {
      val vcs = myVcs.subList(blockRange.start1, blockRange.end1)
      val current = myCurrent.subList(blockRange.start2, blockRange.end2)

      val result = ArrayList<InnerRange>()
      val iwIterable = ByLine.compare(vcs, current, ComparisonPolicy.IGNORE_WHITESPACES, DumbProgressIndicator.INSTANCE)
      for (pair in DiffIterableUtil.iterateAll(iwIterable)) {
        val range = pair.first
        val equals = pair.second

        val type = if (equals) Range.EQUAL else getChangeType(range.start1, range.end1, range.start2, range.end2)
        result.add(InnerRange(range.start2, range.end2,
                              type))
      }
      result.trimToSize()
      return result
    }
    catch (e: DiffTooBigException) {
      return null
    }

  }
}

private fun getChangeType(vcsStart: Int, vcsEnd: Int, currentStart: Int, currentEnd: Int): Byte {
  val deleted = vcsEnd - vcsStart
  val inserted = currentEnd - currentStart
  if (deleted > 0 && inserted > 0) return Range.MODIFIED
  if (deleted > 0) return Range.DELETED
  if (inserted > 0) return Range.INSERTED
  return Range.EQUAL
}
