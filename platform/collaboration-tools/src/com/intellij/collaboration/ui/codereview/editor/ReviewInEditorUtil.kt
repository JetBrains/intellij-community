// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.diff.util.Range
import com.intellij.openapi.diff.LineStatusMarkerColorScheme
import com.intellij.openapi.editor.Editor
import com.intellij.ui.JBColor
import java.awt.Color

object ReviewInEditorUtil {
  val REVIEW_CHANGES_STATUS_COLOR: JBColor =
    JBColor.namedColor("Review.Editor.Line.Status.Marker", JBColor(0xF8A0DF, 0x8A4175))

  val REVIEW_STATUS_MARKER_COLOR_SCHEME: LineStatusMarkerColorScheme =
    object : LineStatusMarkerColorScheme() {
      override fun getColor(editor: Editor, type: Byte): Color = REVIEW_CHANGES_STATUS_COLOR
      override fun getIgnoredBorderColor(editor: Editor, type: Byte): Color = REVIEW_CHANGES_STATUS_COLOR
      override fun getErrorStripeColor(type: Byte): Color = REVIEW_CHANGES_STATUS_COLOR
    }

  fun transferLineToAfter(ranges: List<Range>, line: Int): Int {
    if (ranges.isEmpty()) return line
    var result = line
    for (range in ranges) {
      if (line in range.start1 until range.end1) {
        return (range.end2 - 1).coerceAtLeast(0)
      }

      if (range.end1 > line) return result

      val length1 = range.end1 - range.start1
      val length2 = range.end2 - range.start2
      result += length2 - length1
    }
    return result
  }

  fun transferLineFromAfter(ranges: List<Range>, line: Int, approximate: Boolean = false): Int? {
    if (ranges.isEmpty()) return line
    var result = line
    for (range in ranges) {
      if (line < range.start2) return result

      if (line in range.start2 until range.end2) {
        return if (approximate) range.end1 else null
      }

      val length1 = range.end1 - range.start1
      val length2 = range.end2 - range.start2
      result -= length2 - length1
    }
    return result
  }
}