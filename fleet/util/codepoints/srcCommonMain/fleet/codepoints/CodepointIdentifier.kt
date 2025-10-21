// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.codepoints

import fleet.util.binarySearch

fun isCodepointInRanges(codepoint: Int, ranges: IntArray): Boolean {
  val part = ranges.binarySearch(codepoint)
  return when {
    part >= 0 -> true
    else -> {
      // If the preceding number is the start of a range (even numbers) -> in range
      part % 2 == 0
    }
  }
}
