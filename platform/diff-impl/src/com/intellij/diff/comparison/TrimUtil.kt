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
package com.intellij.diff.comparison

import com.intellij.diff.util.IntPair
import com.intellij.diff.util.MergeRange
import com.intellij.diff.util.Range
import com.intellij.openapi.util.text.StringUtil.isWhiteSpace

@Suppress("NAME_SHADOWING")
object TrimUtil {
  @JvmStatic fun isPunctuation(c: Char): Boolean {
    if (c == '_') return false
    val b = c.toInt()
    return b >= 33 && b <= 47 || // !"#$%&'()*+,-./
           b >= 58 && b <= 64 || // :;<=>?@
           b >= 91 && b <= 96 || // [\]^_`
           b >= 123 && b <= 126  // {|}~
  }

  @JvmStatic fun isAlpha(c: Char): Boolean {
    return !isWhiteSpace(c) && !isPunctuation(c)
  }

  //
  // Trim
  //

  @JvmStatic fun trim(text1: CharSequence, text2: CharSequence,
                      start1: Int, start2: Int, end1: Int, end2: Int): Range {
    var start1 = start1
    var start2 = start2
    var end1 = end1
    var end2 = end2

    start1 = trimStart(text1, start1, end1)
    end1 = trimEnd(text1, start1, end1)
    start2 = trimStart(text2, start2, end2)
    end2 = trimEnd(text2, start2, end2)

    return Range(start1, end1, start2, end2)
  }

  @JvmStatic fun trim(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                      start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int): MergeRange {
    var start1 = start1
    var start2 = start2
    var start3 = start3
    var end1 = end1
    var end2 = end2
    var end3 = end3

    start1 = trimStart(text1, start1, end1)
    end1 = trimEnd(text1, start1, end1)
    start2 = trimStart(text2, start2, end2)
    end2 = trimEnd(text2, start2, end2)
    start3 = trimStart(text3, start3, end3)
    end3 = trimEnd(text3, start3, end3)

    return MergeRange(start1, end1, start2, end2, start3, end3)
  }

  @JvmStatic fun trim(text: CharSequence, start: Int, end: Int): IntPair {
    var start = start
    var end = end

    start = trimStart(text, start, end)
    end = trimEnd(text, start, end)

    return IntPair(start, end)
  }

  @JvmStatic fun trimStart(text: CharSequence, start: Int, end: Int): Int {
    var start = start

    while (start < end) {
      val c = text[start]
      if (!isWhiteSpace(c)) break
      start++
    }
    return start
  }

  @JvmStatic fun trimEnd(text: CharSequence, start: Int, end: Int): Int {
    var end = end

    while (start < end) {
      val c = text[end - 1]
      if (!isWhiteSpace(c)) break
      end--
    }
    return end
  }

  //
  // Expand
  //

  @JvmStatic fun expand(text1: List<*>, text2: List<*>,
                        start1: Int, start2: Int, end1: Int, end2: Int): Range {
    var start1 = start1
    var start2 = start2
    var end1 = end1
    var end2 = end2

    val count1 = expandForward(text1, text2, start1, start2, end1, end2)
    start1 += count1
    start2 += count1

    val count2 = expandBackward(text1, text2, start1, start2, end1, end2)
    end1 -= count2
    end2 -= count2

    return Range(start1, end1, start2, end2)
  }

  @JvmStatic fun expand(text1: CharSequence, text2: CharSequence,
                        start1: Int, start2: Int, end1: Int, end2: Int): Range {
    var start1 = start1
    var start2 = start2
    var end1 = end1
    var end2 = end2

    val count1 = expandForward(text1, text2, start1, start2, end1, end2)
    start1 += count1
    start2 += count1

    val count2 = expandBackward(text1, text2, start1, start2, end1, end2)
    end1 -= count2
    end2 -= count2

    return Range(start1, end1, start2, end2)
  }

  @JvmStatic fun expandW(text1: CharSequence, text2: CharSequence,
                         start1: Int, start2: Int, end1: Int, end2: Int): Range {
    var start1 = start1
    var start2 = start2
    var end1 = end1
    var end2 = end2

    val count1 = expandForwardW(text1, text2, start1, start2, end1, end2)
    start1 += count1
    start2 += count1

    val count2 = expandBackwardW(text1, text2, start1, start2, end1, end2)
    end1 -= count2
    end2 -= count2

    return Range(start1, end1, start2, end2)
  }

  @JvmStatic fun expandW(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                         start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int): MergeRange {
    var start1 = start1
    var start2 = start2
    var start3 = start3
    var end1 = end1
    var end2 = end2
    var end3 = end3

    val count1 = expandForwardW(text1, text2, text3, start1, start2, start3, end1, end2, end3)
    start1 += count1
    start2 += count1
    start3 += count1

    val count2 = expandBackwardW(text1, text2, text3, start1, start2, start3, end1, end2, end3)
    end1 -= count2
    end2 -= count2
    end3 -= count2

    return MergeRange(start1, end1, start2, end2, start3, end3)
  }

  @JvmStatic fun expandForward(text1: CharSequence, text2: CharSequence,
                               start1: Int, start2: Int, end1: Int, end2: Int): Int {
    var start1 = start1
    var start2 = start2

    val oldStart1 = start1
    while (start1 < end1 && start2 < end2) {
      val c1 = text1[start1]
      val c2 = text2[start2]
      if (c1 != c2) break
      start1++
      start2++
    }

    return start1 - oldStart1
  }

  @JvmStatic fun expandForward(text1: List<*>, text2: List<*>,
                               start1: Int, start2: Int, end1: Int, end2: Int): Int {
    var start1 = start1
    var start2 = start2

    val oldStart1 = start1
    while (start1 < end1 && start2 < end2) {
      val c1 = text1[start1]
      val c2 = text2[start2]
      if (c1 != c2) break
      start1++
      start2++
    }

    return start1 - oldStart1
  }

  @JvmStatic fun expandForwardW(text1: CharSequence, text2: CharSequence,
                                start1: Int, start2: Int, end1: Int, end2: Int): Int {
    var start1 = start1
    var start2 = start2

    val oldStart1 = start1
    while (start1 < end1 && start2 < end2) {
      val c1 = text1[start1]
      val c2 = text2[start2]
      if (c1 != c2 || !isWhiteSpace(c1)) break
      start1++
      start2++
    }

    return start1 - oldStart1
  }

  @JvmStatic fun expandBackward(text1: CharSequence, text2: CharSequence,
                                start1: Int, start2: Int, end1: Int, end2: Int): Int {
    var end1 = end1
    var end2 = end2

    val oldEnd1 = end1
    while (start1 < end1 && start2 < end2) {
      val c1 = text1[end1 - 1]
      val c2 = text2[end2 - 1]
      if (c1 != c2) break
      end1--
      end2--
    }

    return oldEnd1 - end1
  }

  @JvmStatic fun expandBackward(text1: List<*>, text2: List<*>,
                                start1: Int, start2: Int, end1: Int, end2: Int): Int {
    var end1 = end1
    var end2 = end2

    val oldEnd1 = end1
    while (start1 < end1 && start2 < end2) {
      val c1 = text1[end1 - 1]
      val c2 = text2[end2 - 1]
      if (c1 != c2) break
      end1--
      end2--
    }

    return oldEnd1 - end1
  }

  @JvmStatic fun expandBackwardW(text1: CharSequence, text2: CharSequence,
                                 start1: Int, start2: Int, end1: Int, end2: Int): Int {
    var end1 = end1
    var end2 = end2

    val oldEnd1 = end1
    while (start1 < end1 && start2 < end2) {
      val c1 = text1[end1 - 1]
      val c2 = text2[end2 - 1]
      if (c1 != c2 || !isWhiteSpace(c1)) break
      end1--
      end2--
    }

    return oldEnd1 - end1
  }

  @JvmStatic fun expandForwardW(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                                start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int): Int {
    var start1 = start1
    var start2 = start2
    var start3 = start3

    val oldStart1 = start1
    while (start1 < end1 && start2 < end2 && start3 < end3) {
      val c1 = text1[start1]
      val c2 = text2[start2]
      val c3 = text3[start3]
      if (c1 != c2 || c1 != c3 || !isWhiteSpace(c1)) break
      start1++
      start2++
      start3++
    }

    return start1 - oldStart1
  }

  @JvmStatic fun expandBackwardW(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                                 start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int): Int {
    var end1 = end1
    var end2 = end2
    var end3 = end3

    val oldEnd1 = end1
    while (start1 < end1 && start2 < end2 && start3 < end3) {
      val c1 = text1[end1 - 1]
      val c2 = text2[end2 - 1]
      val c3 = text3[end3 - 1]
      if (c1 != c2 || c1 != c3 || !isWhiteSpace(c1)) break
      end1--
      end2--
      end3--
    }

    return oldEnd1 - end1
  }

  @JvmStatic fun expandForwardIW(text1: CharSequence, text2: CharSequence,
                                 start1: Int, start2: Int, end1: Int, end2: Int): IntPair {
    var start1 = start1
    var start2 = start2

    while (start1 < end1 && start2 < end2) {
      val c1 = text1[start1]
      val c2 = text2[start2]

      if (c1 == c2) {
        start1++
        start2++
        continue
      }

      var skipped = false
      if (isWhiteSpace(c1)) {
        skipped = true
        start1++
      }
      if (isWhiteSpace(c2)) {
        skipped = true
        start2++
      }
      if (!skipped) break
    }

    start1 = trimStart(text1, start1, end1)
    start2 = trimStart(text2, start2, end2)

    return IntPair(start1, start2)
  }

  @JvmStatic fun expandBackwardIW(text1: CharSequence, text2: CharSequence,
                                  start1: Int, start2: Int, end1: Int, end2: Int): IntPair {
    var end1 = end1
    var end2 = end2

    while (start1 < end1 && start2 < end2) {
      val c1 = text1[end1 - 1]
      val c2 = text2[end2 - 1]

      if (c1 == c2) {
        end1--
        end2--
        continue
      }

      var skipped = false
      if (isWhiteSpace(c1)) {
        skipped = true
        end1--
      }
      if (isWhiteSpace(c2)) {
        skipped = true
        end2--
      }
      if (!skipped) break
    }

    end1 = trimEnd(text1, start1, end1)
    end2 = trimEnd(text2, start2, end2)

    return IntPair(end1, end2)
  }

  @JvmStatic fun expandIW(text1: CharSequence, text2: CharSequence,
                          start1: Int, start2: Int, end1: Int, end2: Int): Range {
    var start1 = start1
    var start2 = start2
    var end1 = end1
    var end2 = end2

    val start = expandForwardIW(text1, text2, start1, start2, end1, end2)
    start1 = start.val1
    start2 = start.val2

    val end = expandBackwardIW(text1, text2, start1, start2, end1, end2)
    end1 = end.val1
    end2 = end.val2

    return Range(start1, end1, start2, end2)
  }


  //
  // Misc
  //

  @JvmStatic fun expand(text1: CharSequence, text2: CharSequence, range: Range): Range {
    return expand(text1, text2, range.start1, range.start2, range.end1, range.end2)
  }

  @JvmStatic fun expandW(text1: CharSequence, text2: CharSequence, range: Range): Range {
    return expandW(text1, text2, range.start1, range.start2, range.end1, range.end2)
  }

  @JvmStatic fun trim(text1: CharSequence, text2: CharSequence, range: Range): Range {
    return trim(text1, text2, range.start1, range.start2, range.end1, range.end2)
  }

  @JvmStatic fun trim(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                      range: MergeRange): MergeRange {
    return trim(text1, text2, text3, range.start1, range.start2, range.start3, range.end1, range.end2, range.end3)
  }

  @JvmStatic fun expandW(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                         range: MergeRange): MergeRange {
    return expandW(text1, text2, text3, range.start1, range.start2, range.start3, range.end1, range.end2, range.end3)
  }

  @JvmStatic fun expandIW(text1: CharSequence, text2: CharSequence): Range {
    return expandIW(text1, text2, 0, 0, text1.length, text2.length)
  }

  //
  // Equality
  //

  @JvmStatic fun isEquals(text1: CharSequence, text2: CharSequence,
                          range: Range): Boolean {
    val sequence1 = text1.subSequence(range.start1, range.end1)
    val sequence2 = text2.subSequence(range.start2, range.end2)
    return ComparisonUtil.isEquals(sequence1, sequence2, ComparisonPolicy.DEFAULT)
  }

  @JvmStatic fun isEqualsIW(text1: CharSequence, text2: CharSequence,
                            range: Range): Boolean {
    val sequence1 = text1.subSequence(range.start1, range.end1)
    val sequence2 = text2.subSequence(range.start2, range.end2)
    return ComparisonUtil.isEquals(sequence1, sequence2, ComparisonPolicy.IGNORE_WHITESPACES)
  }

  @JvmStatic fun isEquals(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                          range: MergeRange): Boolean {
    val sequence1 = text1.subSequence(range.start1, range.end1)
    val sequence2 = text2.subSequence(range.start2, range.end2)
    val sequence3 = text3.subSequence(range.start3, range.end3)
    return ComparisonUtil.isEquals(sequence2, sequence1, ComparisonPolicy.DEFAULT) &&
           ComparisonUtil.isEquals(sequence2, sequence3, ComparisonPolicy.DEFAULT)
  }

  @JvmStatic fun isEqualsIW(text1: CharSequence, text2: CharSequence, text3: CharSequence,
                            range: MergeRange): Boolean {
    val sequence1 = text1.subSequence(range.start1, range.end1)
    val sequence2 = text2.subSequence(range.start2, range.end2)
    val sequence3 = text3.subSequence(range.start3, range.end3)
    return ComparisonUtil.isEquals(sequence2, sequence1, ComparisonPolicy.IGNORE_WHITESPACES) &&
           ComparisonUtil.isEquals(sequence2, sequence3, ComparisonPolicy.IGNORE_WHITESPACES)
  }
}
