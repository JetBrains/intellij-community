// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.ActiveGutterRenderer
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle
import java.awt.event.MouseEvent

@ApiStatus.Internal
abstract class ActiveLineStatusGutterMarkerRenderer
  : LineStatusGutterMarkerRenderer(), ActiveGutterRenderer {

  override fun calcBounds(editor: Editor, lineNum: Int, preferredBounds: Rectangle): Rectangle? {
    val ranges = getPaintedRanges() ?: return null
    return LineStatusMarkerDrawUtil.calcBounds(ranges, editor, lineNum)
  }

  override fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
    val ranges = getPaintedRanges() ?: return false
    val selectedRanges = LineStatusMarkerDrawUtil.getSelectedRanges(ranges, editor, e.y)
    return !selectedRanges.isEmpty() && canDoAction(editor, selectedRanges, e)
  }

  /**
   * Check if action can be performed for specified [ranges]
   */
  protected abstract fun canDoAction(editor: Editor, ranges: List<Range>, e: MouseEvent): Boolean

  override fun doAction(editor: Editor, e: MouseEvent) {
    val ranges = getPaintedRanges() ?: return
    val selectedRanges = LineStatusMarkerDrawUtil.getSelectedRanges(ranges, editor, e.y)
    if (!selectedRanges.isEmpty()) {
      e.consume()
      doAction(editor, selectedRanges, e)
    }
  }

  /**
   * Perform an action for specified [ranges]
   */
  protected abstract fun doAction(editor: Editor, ranges: List<Range>, e: MouseEvent)

  override fun getAccessibleName(): String = DiffBundle.message("vcs.marker.changed.line")
}