package com.intellij.codeInsight.codeVision.ui.renderers

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import java.awt.Rectangle

interface CodeVisionRenderer : EditorCustomElementRenderer {
  fun hoveredEntry(inlay: Inlay<*>, x: Int, y: Int): CodeVisionEntry?
  fun entryBounds(inlay: Inlay<*>, element: CodeVisionEntry): Rectangle?
}