package com.intellij.codeInsight.codeVision.ui.renderers

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.hints.presentation.InputHandler
import com.intellij.openapi.editor.EditorCustomElementRenderer
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

@ApiStatus.Internal
interface CodeVisionInlayRenderer : EditorCustomElementRenderer, InputHandler {
  fun calculateCodeVisionEntryBounds(element: CodeVisionEntry) : Rectangle?
}