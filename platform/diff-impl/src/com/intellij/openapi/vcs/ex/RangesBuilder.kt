/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.diff.comparison.*
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.comparison.iterables.DiffIterableUtil.fair
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.DumbProgressIndicator
import java.util.*

fun createRanges(current: List<String>,
                 vcs: List<String>,
                 currentShift: Int,
                 vcsShift: Int,
                 innerWhitespaceChanges: Boolean): List<LstRange> {
  val iterable = compareLines(vcs, current)
  return iterable.iterateChanges().map {
    val inner = if (innerWhitespaceChanges) createInnerRanges(vcs.subList(it.start1, it.end1), current.subList(it.start2, it.end2)) else null
    LstRange(it.start2 + currentShift, it.end2 + currentShift, it.start1 + vcsShift, it.end1 + vcsShift, inner)
  }
}

fun createRanges(current: Document, vcs: Document): List<LstRange> {
  return createRanges(current.immutableCharSequence, vcs.immutableCharSequence, current.lineOffsets, vcs.lineOffsets)
}

fun createRanges(current: CharSequence, vcs: CharSequence): List<LstRange> {
  return createRanges(current, vcs, current.lineOffsets, vcs.lineOffsets)
}

private fun createRanges(current: CharSequence,
                         vcs: CharSequence,
                         currentLineOffsets: LineOffsets,
                         vcsLineOffsets: LineOffsets): List<LstRange> {
  val iterable = compareLines(vcs, current, vcsLineOffsets, currentLineOffsets)
  return iterable.iterateChanges().map { LstRange(it.start2, it.end2, it.start1, it.end1) }
}


fun compareLines(text1: CharSequence,
                 text2: CharSequence,
                 lineOffsets1: LineOffsets,
                 lineOffsets2: LineOffsets): FairDiffIterable {
  val lines1 = DiffUtil.getLines(text1, lineOffsets1)
  val lines2 = DiffUtil.getLines(text2, lineOffsets2)
  return compareLines(lines1, lines2)
}

fun compareLines(lineRange: Range,
                 text1: CharSequence,
                 text2: CharSequence,
                 lineOffsets1: LineOffsets,
                 lineOffsets2: LineOffsets): FairDiffIterable {
  val lines1 = DiffUtil.getLines(text1, lineOffsets1, lineRange.start1, lineRange.end1)
  val lines2 = DiffUtil.getLines(text2, lineOffsets2, lineRange.start2, lineRange.end2)
  return compareLines(lines1, lines2)
}

fun tryCompareLines(lineRange: Range,
                    text1: CharSequence,
                    text2: CharSequence,
                    lineOffsets1: LineOffsets,
                    lineOffsets2: LineOffsets): FairDiffIterable? {
  val lines1 = DiffUtil.getLines(text1, lineOffsets1, lineRange.start1, lineRange.end1)
  val lines2 = DiffUtil.getLines(text2, lineOffsets2, lineRange.start2, lineRange.end2)
  return tryCompareLines(lines1, lines2)
}

fun fastCompareLines(lineRange: Range,
                     text1: CharSequence,
                     text2: CharSequence,
                     lineOffsets1: LineOffsets,
                     lineOffsets2: LineOffsets): FairDiffIterable {
  val lines1 = DiffUtil.getLines(text1, lineOffsets1, lineRange.start1, lineRange.end1)
  val lines2 = DiffUtil.getLines(text2, lineOffsets2, lineRange.start2, lineRange.end2)
  return fastCompareLines(lines1, lines2)
}


fun createInnerRanges(lineRange: Range,
                      text1: CharSequence,
                      text2: CharSequence,
                      lineOffsets1: LineOffsets,
                      lineOffsets2: LineOffsets): List<LstInnerRange> {
  val lines1 = DiffUtil.getLines(text1, lineOffsets1, lineRange.start1, lineRange.end1)
  val lines2 = DiffUtil.getLines(text2, lineOffsets2, lineRange.start2, lineRange.end2)
  return createInnerRanges(lines1, lines2)
}

private fun compareLines(lines1: List<String>,
                         lines2: List<String>): FairDiffIterable {
  val iwIterable: FairDiffIterable = safeCompareLines(lines1, lines2, ComparisonPolicy.IGNORE_WHITESPACES)
  return processLines(lines1, lines2, iwIterable)
}

private fun tryCompareLines(lines1: List<String>,
                            lines2: List<String>): FairDiffIterable? {
  val iwIterable: FairDiffIterable = tryCompareLines(lines1, lines2, ComparisonPolicy.IGNORE_WHITESPACES) ?: return null
  return processLines(lines1, lines2, iwIterable)
}

private fun fastCompareLines(lines1: List<String>,
                             lines2: List<String>): FairDiffIterable {
  val iwIterable: FairDiffIterable = fastCompareLines(lines1, lines2, ComparisonPolicy.IGNORE_WHITESPACES)
  return processLines(lines1, lines2, iwIterable)
}

/**
 * Compare lines, preferring non-optimal but less confusing results for whitespace-only changed lines
 * Ex: "X\n\nY\nZ" vs " X\n Y\n\n Z" should be a single big change, rather than 2 changes separated by "matched" empty line.
 */
private fun processLines(lines1: List<String>,
                         lines2: List<String>,
                         iwIterable: FairDiffIterable): FairDiffIterable {
  val builder = DiffIterableUtil.ExpandChangeBuilder(lines1, lines2)
  for (range in iwIterable.unchanged()) {
    val count = range.end1 - range.start1
    for (i in 0 until count) {
      val index1 = range.start1 + i
      val index2 = range.start2 + i
      if (lines1[index1] == lines2[index2]) {
        builder.markEqual(index1, index2)
      }
    }
  }

  return fair(builder.finish())
}


private fun createInnerRanges(lines1: List<String>,
                              lines2: List<String>): List<LstInnerRange> {
  val iwIterable: FairDiffIterable = safeCompareLines(lines1, lines2, ComparisonPolicy.IGNORE_WHITESPACES)

  val result = ArrayList<LstInnerRange>()
  for (pair in DiffIterableUtil.iterateAll(iwIterable)) {
    val range = pair.first
    val equals = pair.second
    result.add(LstInnerRange(range.start2, range.end2, getChangeType(range, equals)))
  }
  result.trimToSize()
  return result
}

private fun getChangeType(range: Range, equals: Boolean): Byte {
  if (equals) return LstRange.EQUAL
  val deleted = range.end1 - range.start1
  val inserted = range.end2 - range.start2
  if (deleted > 0 && inserted > 0) return LstRange.MODIFIED
  if (deleted > 0) return LstRange.DELETED
  if (inserted > 0) return LstRange.INSERTED
  return LstRange.EQUAL
}


private fun safeCompareLines(lines1: List<String>, lines2: List<String>, comparisonPolicy: ComparisonPolicy): FairDiffIterable {
  return tryCompareLines(lines1, lines2, comparisonPolicy) ?: fastCompareLines(lines1, lines2, comparisonPolicy)
}

private fun tryCompareLines(lines1: List<String>, lines2: List<String>, comparisonPolicy: ComparisonPolicy): FairDiffIterable? {
  try {
    return ByLine.compare(lines1, lines2, comparisonPolicy, DumbProgressIndicator.INSTANCE)
  }
  catch (e: DiffTooBigException) {
    return null
  }
}

private fun fastCompareLines(lines1: List<String>, lines2: List<String>, comparisonPolicy: ComparisonPolicy): FairDiffIterable {
  val range = expand(lines1, lines2, 0, 0, lines1.size, lines2.size,
                     { line1, line2 -> ComparisonUtil.isEquals(line1, line2, comparisonPolicy) })
  val ranges = if (range.isEmpty) emptyList() else listOf(range)
  return fair(DiffIterableUtil.create(ranges, lines1.size, lines2.size))
}


internal operator fun <T> Side.get(v1: T, v2: T): T = if (isLeft) v1 else v2
internal fun Range.start(side: Side) = side[start1, start2]
internal fun Range.end(side: Side) = side[end1, end2]

internal val Document.lineOffsets: LineOffsets get() = LineOffsetsUtil.create(this)
internal val CharSequence.lineOffsets: LineOffsets get() = LineOffsetsUtil.create(this)