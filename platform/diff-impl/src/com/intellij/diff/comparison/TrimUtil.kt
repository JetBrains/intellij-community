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
@file:JvmName("TrimUtil")
@file:Suppress("NAME_SHADOWING")

package com.intellij.diff.comparison

import com.intellij.diff.util.IntPair
import com.intellij.diff.util.MergeRange
import com.intellij.diff.util.Range
import com.intellij.openapi.util.text.StringUtil.isWhiteSpace
import java.util.*

fun isPunctuation(c: Char): Boolean {
  if (c == '_') return false
  val b = c.toInt()
  return b >= 33 && b <= 47 || // !"#$%&'()*+,-./
         b >= 58 && b <= 64 || // :;<=>?@
         b >= 91 && b <= 96 || // [\]^_`
         b >= 123 && b <= 126  // {|}~
}

fun isAlpha(c: Char): Boolean {
  return !isWhiteSpace(c) && !isPunctuation(c)
}


fun trim(text: CharSequence, start: Int, end: Int): IntPair {
  return trim(start, end,
              { index -> isWhiteSpace(text[index]) })
}

fun trim(start: Int, end: Int, ignored: BitSet): IntPair {
  return trim(start, end,
              { index -> ignored[index] })
}

fun trimStart(text: CharSequence, start: Int, end: Int): Int {
  return trimStart(start, end,
                   { index -> isWhiteSpace(text[index]) })
}

fun trimEnd(text: CharSequence, start: Int, end: Int): Int {
  return trimEnd(start, end,
                 { index -> isWhiteSpace(text[index]) })
}


fun trim(text1: CharSequence, text2: CharSequence,
         start1: Int, start2: Int, end1: Int, end2: Int): Range {
  return trim(start1, start2, end1, end2,
              { index -> isWhiteSpace(text1[index]) },
              { index -> isWhiteSpace(text2[index]) })
}

fun trim(text1: CharSequence, text2: CharSequence, text3: CharSequence,
         start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int): MergeRange {
  return trim(start1, start2, start3, end1, end2, end3,
              { index -> isWhiteSpace(text1[index]) },
              { index -> isWhiteSpace(text2[index]) },
              { index -> isWhiteSpace(text3[index]) })
}


fun expand(text1: List<*>, text2: List<*>,
           start1: Int, start2: Int, end1: Int, end2: Int): Range {
  return expand(start1, start2, end1, end2,
                { index1, index2 -> text1[index1] == text2[index2] })
}

fun expandForward(text1: List<*>, text2: List<*>,
                  start1: Int, start2: Int, end1: Int, end2: Int): Int {
  return expandForward(start1, start2, end1, end2,
                       { index1, index2 -> text1[index1] == text2[index2] })
}

fun expandBackward(text1: List<*>, text2: List<*>,
                   start1: Int, start2: Int, end1: Int, end2: Int): Int {
  return expandBackward(start1, start2, end1, end2,
                        { index1, index2 -> text1[index1] == text2[index2] })
}


fun <T> expand(text1: List<T>, text2: List<T>,
               start1: Int, start2: Int, end1: Int, end2: Int,
               equals: (T, T) -> Boolean): Range {
  return expand(start1, start2, end1, end2,
                { index1, index2 -> equals(text1[index1], text2[index2]) })
}


fun expand(text1: CharSequence, text2: CharSequence,
           start1: Int, start2: Int, end1: Int, end2: Int): Range {
  return expand(start1, start2, end1, end2,
                { index1, index2 -> text1[index1] == text2[index2] })
}

fun expandForward(text1: CharSequence, text2: CharSequence,
                  start1: Int, start2: Int, end1: Int, end2: Int): Int {
  return expandForward(start1, start2, end1, end2,
                       { index1, index2 -> text1[index1] == text2[index2] })
}

fun expandBackward(text1: CharSequence, text2: CharSequence,
                   start1: Int, start2: Int, end1: Int, end2: Int): Int {
  return expandBackward(start1, start2, end1, end2,
                        { index1, index2 -> text1[index1] == text2[index2] })
}


fun expandWhitespaces(text1: CharSequence, text2: CharSequence,
                      start1: Int, start2: Int, end1: Int, end2: Int): Range {
  return expandIgnored(start1, start2, end1, end2,
                       { index1, index2 -> text1[index1] == text2[index2] },
                       { index -> isWhiteSpace(text1[index]) })
}


fun expandWhitespacesForward(text1: CharSequence, text2: CharSequence,
                             start1: Int, start2: Int, end1: Int, end2: Int): Int {
  return expandIgnoredForward(start1, start2, end1, end2,
                              { index1, index2 -> text1[index1] == text2[index2] },
                              { index -> isWhiteSpace(text1[index]) })
}


fun expandWhitespacesBackward(text1: CharSequence, text2: CharSequence,
                              start1: Int, start2: Int, end1: Int, end2: Int): Int {
  return expandIgnoredBackward(start1, start2, end1, end2,
                               { index1, index2 -> text1[index1] == text2[index2] },
                               { index -> isWhiteSpace(text1[index]) })
}


fun expandWhitespaces(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                      start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int): MergeRange {
  return expandIgnored(start1, start2, start3, end1, end2, end3,
                       { index1, index2 -> text1[index1] == text2[index2] },
                       { index1, index3 -> text1[index1] == text3[index3] },
                       { index -> isWhiteSpace(text1[index]) })
}

fun expandWhitespacesForward(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                             start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int): Int {
  return expandIgnoredForward(start1, start2, start3, end1, end2, end3,
                              { index1, index2 -> text1[index1] == text2[index2] },
                              { index1, index3 -> text1[index1] == text3[index3] },
                              { index -> isWhiteSpace(text1[index]) })
}

fun expandWhitespacesBackward(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                              start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int): Int {
  return expandIgnoredBackward(start1, start2, start3, end1, end2, end3,
                               { index1, index2 -> text1[index1] == text2[index2] },
                               { index1, index3 -> text1[index1] == text3[index3] },
                               { index -> isWhiteSpace(text1[index]) })
}


fun <T> trimExpandList(text1: List<T>, text2: List<T>,
                       start1: Int, start2: Int, end1: Int, end2: Int,
                       equals: (T, T) -> Boolean,
                       ignored1: (T) -> Boolean,
                       ignored2: (T) -> Boolean): Range {
  return trimExpand(start1, start2, end1, end2,
                    { index1, index2 -> equals(text1[index1], text2[index2]) },
                    { index -> ignored1(text1[index]) },
                    { index -> ignored2(text2[index]) })
}

fun trimExpandText(text1: CharSequence, text2: CharSequence,
                   start1: Int, start2: Int, end1: Int, end2: Int,
                   ignored1: BitSet,
                   ignored2: BitSet): Range {
  return trimExpand(start1, start2, end1, end2,
                    { index1, index2 -> text1[index1] == text2[index2] },
                    { index -> ignored1[index] },
                    { index -> ignored2[index] })
}


fun trim(text1: CharSequence, text2: CharSequence,
         range: Range): Range {
  return trim(text1, text2, range.start1, range.start2, range.end1, range.end2)
}

fun trim(text1: CharSequence, text2: CharSequence, text3: CharSequence,
         range: MergeRange): MergeRange {
  return trim(text1, text2, text3, range.start1, range.start2, range.start3, range.end1, range.end2, range.end3)
}

fun expand(text1: CharSequence, text2: CharSequence,
           range: Range): Range {
  return expand(text1, text2, range.start1, range.start2, range.end1, range.end2)
}

fun expandWhitespaces(text1: CharSequence, text2: CharSequence,
                      range: Range): Range {
  return expandWhitespaces(text1, text2, range.start1, range.start2, range.end1, range.end2)
}

fun expandWhitespaces(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                      range: MergeRange): MergeRange {
  return expandWhitespaces(text1, text2, text3, range.start1, range.start2, range.start3, range.end1, range.end2, range.end3)
}


fun isEquals(text1: CharSequence, text2: CharSequence,
             range: Range): Boolean {
  val sequence1 = text1.subSequence(range.start1, range.end1)
  val sequence2 = text2.subSequence(range.start2, range.end2)
  return ComparisonUtil.isEquals(sequence1, sequence2, ComparisonPolicy.DEFAULT)
}

fun isEqualsIgnoreWhitespaces(text1: CharSequence, text2: CharSequence,
                              range: Range): Boolean {
  val sequence1 = text1.subSequence(range.start1, range.end1)
  val sequence2 = text2.subSequence(range.start2, range.end2)
  return ComparisonUtil.isEquals(sequence1, sequence2, ComparisonPolicy.IGNORE_WHITESPACES)
}

fun isEquals(text1: CharSequence, text2: CharSequence, text3: CharSequence,
             range: MergeRange): Boolean {
  val sequence1 = text1.subSequence(range.start1, range.end1)
  val sequence2 = text2.subSequence(range.start2, range.end2)
  val sequence3 = text3.subSequence(range.start3, range.end3)
  return ComparisonUtil.isEquals(sequence2, sequence1, ComparisonPolicy.DEFAULT) &&
         ComparisonUtil.isEquals(sequence2, sequence3, ComparisonPolicy.DEFAULT)
}

fun isEqualsIgnoreWhitespaces(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                              range: MergeRange): Boolean {
  val sequence1 = text1.subSequence(range.start1, range.end1)
  val sequence2 = text2.subSequence(range.start2, range.end2)
  val sequence3 = text3.subSequence(range.start3, range.end3)
  return ComparisonUtil.isEquals(sequence2, sequence1, ComparisonPolicy.IGNORE_WHITESPACES) &&
         ComparisonUtil.isEquals(sequence2, sequence3, ComparisonPolicy.IGNORE_WHITESPACES)
}

//
// Trim
//

private inline fun trim(start1: Int, start2: Int, end1: Int, end2: Int,
                        ignored1: (Int) -> Boolean,
                        ignored2: (Int) -> Boolean): Range {
  var start1 = start1
  var start2 = start2
  var end1 = end1
  var end2 = end2

  start1 = trimStart(start1, end1, ignored1)
  end1 = trimEnd(start1, end1, ignored1)
  start2 = trimStart(start2, end2, ignored2)
  end2 = trimEnd(start2, end2, ignored2)

  return Range(start1, end1, start2, end2)
}

private inline fun trim(start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int,
                        ignored1: (Int) -> Boolean,
                        ignored2: (Int) -> Boolean,
                        ignored3: (Int) -> Boolean): MergeRange {
  var start1 = start1
  var start2 = start2
  var start3 = start3
  var end1 = end1
  var end2 = end2
  var end3 = end3

  start1 = trimStart(start1, end1, ignored1)
  end1 = trimEnd(start1, end1, ignored1)
  start2 = trimStart(start2, end2, ignored2)
  end2 = trimEnd(start2, end2, ignored2)
  start3 = trimStart(start3, end3, ignored3)
  end3 = trimEnd(start3, end3, ignored3)

  return MergeRange(start1, end1, start2, end2, start3, end3)
}

private inline fun trim(start: Int, end: Int,
                        ignored: (Int) -> Boolean): IntPair {
  var start = start
  var end = end

  start = trimStart(start, end, ignored)
  end = trimEnd(start, end, ignored)

  return IntPair(start, end)
}

private inline fun trimStart(start: Int, end: Int,
                             ignored: (Int) -> Boolean): Int {
  var start = start

  while (start < end) {
    if (!ignored(start)) break
    start++
  }
  return start
}

private inline fun trimEnd(start: Int, end: Int,
                           ignored: (Int) -> Boolean): Int {
  var end = end

  while (start < end) {
    if (!ignored(end - 1)) break
    end--
  }
  return end
}

//
// Expand
//

private inline fun expand(start1: Int, start2: Int, end1: Int, end2: Int,
                          equals: (Int, Int) -> Boolean): Range {
  var start1 = start1
  var start2 = start2
  var end1 = end1
  var end2 = end2

  val count1 = expandForward(start1, start2, end1, end2, equals)
  start1 += count1
  start2 += count1

  val count2 = expandBackward(start1, start2, end1, end2, equals)
  end1 -= count2
  end2 -= count2

  return Range(start1, end1, start2, end2)
}

private inline fun expandForward(start1: Int, start2: Int, end1: Int, end2: Int,
                                 equals: (Int, Int) -> Boolean): Int {
  var start1 = start1
  var start2 = start2

  val oldStart1 = start1
  while (start1 < end1 && start2 < end2) {
    if (!equals(start1, start2)) break
    start1++
    start2++
  }

  return start1 - oldStart1
}

private inline fun expandBackward(start1: Int, start2: Int, end1: Int, end2: Int,
                                  equals: (Int, Int) -> Boolean): Int {
  var end1 = end1
  var end2 = end2

  val oldEnd1 = end1
  while (start1 < end1 && start2 < end2) {
    if (!equals(end1 - 1, end2 - 1)) break
    end1--
    end2--
  }

  return oldEnd1 - end1
}

//
// Expand Ignored
//

private inline fun expandIgnored(start1: Int, start2: Int, end1: Int, end2: Int,
                                 equals: (Int, Int) -> Boolean,
                                 ignored1: (Int) -> Boolean): Range {
  var start1 = start1
  var start2 = start2
  var end1 = end1
  var end2 = end2

  val count1 = expandIgnoredForward(start1, start2, end1, end2, equals, ignored1)
  start1 += count1
  start2 += count1

  val count2 = expandIgnoredBackward(start1, start2, end1, end2, equals, ignored1)
  end1 -= count2
  end2 -= count2

  return Range(start1, end1, start2, end2)
}

private inline fun expandIgnoredForward(start1: Int, start2: Int, end1: Int, end2: Int,
                                        equals: (Int, Int) -> Boolean,
                                        ignored1: (Int) -> Boolean): Int {
  var start1 = start1
  var start2 = start2

  val oldStart1 = start1
  while (start1 < end1 && start2 < end2) {
    if (!equals(start1, start2)) break
    if (!ignored1(start1)) break
    start1++
    start2++
  }

  return start1 - oldStart1
}

private inline fun expandIgnoredBackward(start1: Int, start2: Int, end1: Int, end2: Int,
                                         equals: (Int, Int) -> Boolean,
                                         ignored1: (Int) -> Boolean): Int {
  var end1 = end1
  var end2 = end2

  val oldEnd1 = end1
  while (start1 < end1 && start2 < end2) {
    if (!equals(end1 - 1, end2 - 1)) break
    if (!ignored1(end1 - 1)) break
    end1--
    end2--
  }

  return oldEnd1 - end1
}

private inline fun expandIgnored(start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int,
                                 equals12: (Int, Int) -> Boolean,
                                 equals13: (Int, Int) -> Boolean,
                                 ignored1: (Int) -> Boolean): MergeRange {
  var start1 = start1
  var start2 = start2
  var start3 = start3
  var end1 = end1
  var end2 = end2
  var end3 = end3

  val count1 = expandIgnoredForward(start1, start2, start3, end1, end2, end3, equals12, equals13, ignored1)
  start1 += count1
  start2 += count1
  start3 += count1

  val count2 = expandIgnoredBackward(start1, start2, start3, end1, end2, end3, equals12, equals13, ignored1)
  end1 -= count2
  end2 -= count2
  end3 -= count2

  return MergeRange(start1, end1, start2, end2, start3, end3)
}

private inline fun expandIgnoredForward(start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int,
                                        equals12: (Int, Int) -> Boolean,
                                        equals13: (Int, Int) -> Boolean,
                                        ignored1: (Int) -> Boolean): Int {
  var start1 = start1
  var start2 = start2
  var start3 = start3

  val oldStart1 = start1
  while (start1 < end1 && start2 < end2 && start3 < end3) {
    if (!equals12(start1, start2)) break
    if (!equals13(start1, start3)) break
    if (!ignored1(start1)) break
    start1++
    start2++
    start3++
  }

  return start1 - oldStart1
}

private inline fun expandIgnoredBackward(start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int,
                                         equals12: (Int, Int) -> Boolean,
                                         equals13: (Int, Int) -> Boolean,
                                         ignored1: (Int) -> Boolean): Int {
  var end1 = end1
  var end2 = end2
  var end3 = end3

  val oldEnd1 = end1
  while (start1 < end1 && start2 < end2 && start3 < end3) {
    if (!equals12(end1 - 1, end2 - 1)) break
    if (!equals13(end1 - 1, end3 - 1)) break
    if (!ignored1(end1 - 1)) break
    end1--
    end2--
    end3--
  }

  return oldEnd1 - end1
}

//
// Trim Expand
//

private inline fun trimExpand(start1: Int, start2: Int, end1: Int, end2: Int,
                              equals: (Int, Int) -> Boolean,
                              ignored1: (Int) -> Boolean,
                              ignored2: (Int) -> Boolean): Range {
  var start1 = start1
  var start2 = start2
  var end1 = end1
  var end2 = end2

  val starts = trimExpandForward(start1, start2, end1, end2, equals, ignored1, ignored2)
  start1 = starts.val1
  start2 = starts.val2

  val ends = trimExpandBackward(start1, start2, end1, end2, equals, ignored1, ignored2)
  end1 = ends.val1
  end2 = ends.val2

  return Range(start1, end1, start2, end2)
}

private inline fun trimExpandForward(start1: Int, start2: Int, end1: Int, end2: Int,
                                     equals: (Int, Int) -> Boolean,
                                     ignored1: (Int) -> Boolean,
                                     ignored2: (Int) -> Boolean): IntPair {
  var start1 = start1
  var start2 = start2

  while (start1 < end1 && start2 < end2) {
    if (equals(start1, start2)) {
      start1++
      start2++
      continue
    }

    var skipped = false
    if (ignored1(start1)) {
      skipped = true
      start1++
    }
    if (ignored2(start2)) {
      skipped = true
      start2++
    }
    if (!skipped) break
  }

  start1 = trimStart(start1, end1, ignored1)
  start2 = trimStart(start2, end2, ignored2)

  return IntPair(start1, start2)
}

private inline fun trimExpandBackward(start1: Int, start2: Int, end1: Int, end2: Int,
                                      equals: (Int, Int) -> Boolean,
                                      ignored1: (Int) -> Boolean,
                                      ignored2: (Int) -> Boolean): IntPair {
  var end1 = end1
  var end2 = end2

  while (start1 < end1 && start2 < end2) {
    if (equals(end1 - 1, end2 - 1)) {
      end1--
      end2--
      continue
    }

    var skipped = false
    if (ignored1(end1 - 1)) {
      skipped = true
      end1--
    }
    if (ignored2(end2 - 1)) {
      skipped = true
      end2--
    }
    if (!skipped) break
  }

  end1 = trimEnd(start1, end1, ignored1)
  end2 = trimEnd(start2, end2, ignored2)

  return IntPair(end1, end2)
}
