/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays

import com.intellij.openapi.editor.impl.EditorImpl
import java.awt.Point
import kotlin.math.max
import kotlin.math.min

/**
 * There was EditorInlaysManager code, which later was replaced by
 * [org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayManager]
 */
object EditorInlaysManager {
  const val INLAY_PRIORITY = 0
}

const val VIEWPORT_INLAY_RANGE = 20

fun calculateViewportRange(editor: EditorImpl): IntRange {
  val viewport = editor.scrollPane.viewport
  val yMin = viewport.viewPosition.y
  val yMax = yMin + viewport.height
  return yMin until yMax
}

fun calculateInlayExpansionRange(editor: EditorImpl, viewportRange: IntRange): IntRange {
  val startLine = editor.xyToLogicalPosition(Point(0, viewportRange.first)).line
  val endLine = editor.xyToLogicalPosition(Point(0, viewportRange.last + 1)).line
  val startOffset = editor.document.getLineStartOffset(max(startLine - VIEWPORT_INLAY_RANGE, 0))
  val endOffset = editor.document.getLineStartOffset(max(min(endLine + VIEWPORT_INLAY_RANGE, editor.document.lineCount - 1), 0))
  return startOffset..endOffset
}