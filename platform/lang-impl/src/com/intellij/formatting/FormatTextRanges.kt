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
package com.intellij.formatting

import com.intellij.openapi.util.TextRange
import java.util.*


class FormatRangesStorage {
  private val rangesByStartOffset = TreeMap<Int, FormatTextRange>()

  fun add(range: TextRange, processHeadingWhitespace: Boolean) {
    if (range.isEmpty) return
    val newRange = FormatTextRange(range, processHeadingWhitespace)

    val nearestBefore = getClosestOrSiblingRange(range)
    if (nearestBefore != null && canBeMerged(nearestBefore, range)) {
      val mergedRange = merge(newRange, nearestBefore)
      rangesByStartOffset.remove(nearestBefore.startOffset)
      rangesByStartOffset.put(mergedRange.startOffset, mergedRange)
      return
    }
    
    assert(rangesByStartOffset[newRange.startOffset] == null)
    rangesByStartOffset.put(newRange.startOffset, newRange)
  }

  private fun canBeMerged(nearestBefore: FormatTextRange, newRange: TextRange): Boolean {
    return newRange.endOffset < nearestBefore.endOffset || newRange.startOffset < nearestBefore.endOffset
  }

  private fun getClosestOrSiblingRange(range: TextRange): FormatTextRange? {
    val closest = rangesByStartOffset.floorEntry(range.endOffset)?.value ?: return null
    if (range.endOffset == closest.startOffset && !closest.isProcessHeadingWhitespace) {
      return rangesByStartOffset.floorEntry(range.endOffset - 1)?.value
    }
    return closest
  }

  private fun merge(first: FormatTextRange, second: FormatTextRange): FormatTextRange {
    val firstByStartOffset = listOf(first, second).sortedBy { it.startOffset }.first()
    val endOffset = Math.max(first.endOffset, second.endOffset)
    val range = TextRange(firstByStartOffset.startOffset, endOffset)
    return FormatTextRange(range, firstByStartOffset.isProcessHeadingWhitespace)
  }

  fun isWhiteSpaceReadOnly(range: TextRange): Boolean {
    return rangesByStartOffset.values.find { !it.isWhitespaceReadOnly(range) } == null
  }
  
  fun isReadOnly(range: TextRange): Boolean {
    return rangesByStartOffset.values.find { !it.isReadOnly(range) } == null
  }
  
  fun getRanges(): List<FormatTextRange> = rangesByStartOffset.values.toList()
  
  fun isEmpty() = rangesByStartOffset.isEmpty()
  
}