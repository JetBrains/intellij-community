// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.diff.util.Range
import com.intellij.util.containers.PeekableIteratorWrapper

private val Range.leftStart: Int get() = start1
private val Range.leftEnd: Int get() = end1
private val Range.rightStart: Int get() = start2
private val Range.rightEnd: Int get() = end2

/**
 * Input: given 3 revisions: A -> B -> C and 2 sets of differences between them: earlyChanges 'A -> B' and laterChanges 'B -> C'.
 * We want to translate this into a list of 'A -> C' offsets excluding laterChanges.
 * If there's a conflict, and we can't precisely map A -> C we split the range and map multiple ranges in C to a single range in A.
 *
 * @see com.intellij.codeInsight.actions.ChangedRangesShifter
 */
object ExcludingApproximateChangedRangesShifter {
  fun shift(earlyChanges: Iterable<Range>, laterChanges: Iterable<Range>): List<Range> {
    val result = mutableListOf<Range>()
    val iLater = PeekableIteratorWrapper(laterChanges.iterator())
    var cShift = 0

    for (earlyRange in earlyChanges) {
      val aStart = earlyRange.leftStart
      val aEnd = earlyRange.leftEnd
      var bStart = earlyRange.rightStart
      val bEnd = earlyRange.rightEnd

      // early range was fully mapped without leftovers
      var fullyMapped = false
      while (iLater.hasNext()) {
        val laterRange = iLater.peek()

        // lines in B
        val leftStart = earlyRange.rightStart
        val leftEnd = earlyRange.rightEnd
        val rightStart = laterRange.leftStart
        val rightEnd = laterRange.leftEnd

        // line number shift for C
        val deleted: Int = laterRange.leftEnd - laterRange.leftStart
        val inserted: Int = laterRange.rightEnd - laterRange.rightStart
        val laterDelta = inserted - deleted

        when {
          // no intersection, "later" before "early"
          rightEnd <= leftStart -> {
            cShift += laterDelta
            iLater.next()
          }
          // no intersection, "later" after "early"
          rightStart >= leftEnd -> {
            break
          }
          rightStart <= leftStart -> {
            // "early" fully inside "later"
            if (rightEnd >= leftEnd) {
              fullyMapped = true
              break
            }
            else {
              // partial intersection at the start
              bStart = rightEnd
              cShift += laterDelta
              iLater.next()
            }
          }
          rightStart > leftStart -> {
            result.add(Range(aStart, aEnd, cShift + bStart, laterRange.rightStart))
            if (rightEnd == leftEnd) {
              // "later" fully inside "early"
              cShift += laterDelta
              fullyMapped = true
              break
            }
            else if (rightEnd < leftEnd) {
              // "later" partially inside "early"
              bStart = rightEnd
              cShift += laterDelta
              iLater.next()
            }
            else {
              // intersection in the end
              fullyMapped = true
              break
            }
          }
        }
      }

      // add leftover
      if (!fullyMapped && bStart <= bEnd) {
        result.add(Range(aStart, aEnd, cShift + bStart, cShift + bEnd))
      }
    }
    return result
  }
}