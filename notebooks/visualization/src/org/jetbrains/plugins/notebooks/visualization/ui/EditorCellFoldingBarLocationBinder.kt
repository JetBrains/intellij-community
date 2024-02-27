package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.asSafely
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayController
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

internal class EditorCellFoldingBarLocationBinder(private val editor: EditorEx, private val foldingBar: EditorCellFoldingBar) {

  private val listener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) {
      foldingBar.setLocation(e.component.location.y, e.component.size.height)
    }
  }

  private var currentController: NotebookCellInlayController? = null
  private var currentInterval: NotebookIntervalPointer? = null

  fun dispose() {
    unbind()
  }

  private fun unbind() {
    currentController?.let {
      it.inlay.renderer.asSafely<JComponent>()?.removeComponentListener(listener)
    }
    currentController = null
  }

  fun bindTo(controller: NotebookCellInlayController?) {
    if (controller != currentController) {
      unbind()
      if (controller != null) {
        controller.inlay.renderer.asSafely<JComponent>()?.addComponentListener(listener)
        currentController = controller
      }
    }
  }

  fun bindTo(interval: NotebookIntervalPointer) {
    currentInterval = interval
  }

  fun updatePositions() {
    val controller = currentController
    if (controller != null) {
      val component = controller.inlay.renderer as JComponent
      foldingBar.setLocation(component.location.y, component.size.height)
    }
    else {
      val interval = currentInterval?.get()
      if (interval != null) {
        val startOffset = editor.document.getLineStartOffset(interval.lines.first)
        val endOffset = editor.document.getLineEndOffset(interval.lines.last)
        val top = editor.offsetToXY(startOffset).y
        val height = editor.offsetToXY(endOffset).y + editor.lineHeight - top
        foldingBar.setLocation(top, height)
      }
    }
  }
}