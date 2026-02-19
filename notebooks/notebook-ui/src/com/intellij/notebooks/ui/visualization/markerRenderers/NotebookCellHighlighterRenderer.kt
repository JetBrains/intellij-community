// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization.markerRenderers

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Graphics

/**
 * Renders rectangle in the right part of the editor to make filled code cells look like rectangles with margins.
 * But mostly it's used as a token to filter notebook cell highlighters.
 */
@Suppress("DuplicatedCode")
object NotebookCellHighlighterRenderer : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    editor as EditorImpl

    val g = graphics.create()
    try {
      val scrollbarWidth = editor.scrollPane.verticalScrollBar.width
      val oldBounds = g.clipBounds
      val visibleArea = editor.scrollingModel.visibleArea
      g.setClip(
        visibleArea.x + visibleArea.width - scrollbarWidth,
        oldBounds.y,
        scrollbarWidth,
        oldBounds.height
      )

      g.color = editor.colorsScheme.defaultBackground
      g.clipBounds.run {
        val fillX = if (editor.editorKind == EditorKind.DIFF && editor.isMirrored) x + 20 else x
        g.fillRect(fillX, y, width, height)
      }
    } finally {
      g.dispose()
    }
  }
}