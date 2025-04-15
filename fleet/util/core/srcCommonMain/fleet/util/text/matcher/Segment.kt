// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

interface Segment {
  val startOffset: Int
  val endOffset: Int

  companion object {
    val EMPTY_ARRAY: Array<Segment?> = arrayOfNulls<Segment>(0)

    val BY_START_OFFSET_THEN_END_OFFSET: Comparator<Segment?> = Comparator { r1: Segment?, r2: Segment? ->
      var result = r1!!.startOffset - r2!!.startOffset
      if (result == 0) result = r1.endOffset - r2.endOffset
      result
    }
  }
}
