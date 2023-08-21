package com.intellij.codeInsight.codeVision.ui.renderers

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.hints.presentation.InputHandler
import com.intellij.openapi.editor.EditorCustomElementRenderer
import java.awt.Rectangle

interface CodeVisionInlayRenderer : EditorCustomElementRenderer, InputHandler {
  fun calculateCodeVisionEntryBounds(element: CodeVisionEntry) : Rectangle?
}