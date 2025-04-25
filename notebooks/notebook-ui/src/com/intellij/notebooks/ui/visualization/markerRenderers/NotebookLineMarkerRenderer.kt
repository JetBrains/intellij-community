package com.intellij.notebooks.ui.visualization.markerRenderers

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import java.awt.Rectangle

abstract class NotebookLineMarkerRenderer(val inlayId: Long? = null) : LineMarkerRendererEx {
  override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.CUSTOM

  protected fun getInlayBounds(editor: EditorEx, linesRange: IntRange): Rectangle? {
    val startOffset = editor.document.getLineStartOffset(linesRange.first)
    val endOffset = editor.document.getLineEndOffset(linesRange.last)
    val inlays = editor.inlayModel.getBlockElementsInRange(startOffset, endOffset)

    val inlay = inlays.firstOrNull { it is RangeMarkerEx && it.id == inlayId }
    return inlay?.bounds
  }
}