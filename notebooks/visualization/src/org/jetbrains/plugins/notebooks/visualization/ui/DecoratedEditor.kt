package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayManager
import java.awt.AWTEvent
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLayer
import javax.swing.SwingUtilities
import javax.swing.plaf.LayerUI

private class DecoratedEditor(private val original: TextEditor) : TextEditor by original {

  private var mouseOverCell: EditorCellView? = null

  private val component = createLayer(original.component)

  init {
    if (!GraphicsEnvironment.isHeadless()) {
      setupScrollPaneListener()
    }
  }

  private fun setupScrollPaneListener() {
    val editorEx = original.editor as EditorEx
    val scrollPane = editorEx.scrollPane
    scrollPane.viewport.addChangeListener {
      editorEx.contentComponent.mousePosition?.let {
        updateMouseOverCell(editorEx.contentComponent, it)
      }
      editorEx.gutterComponentEx.mousePosition?.let {
        updateMouseOverCell(editorEx.gutterComponentEx, it)
      }
    }
  }

  override fun getComponent(): JComponent = component

  override fun getFile(): VirtualFile {
    return original.file
  }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    return original.getState(level)
  }

  override fun dispose() {
    Disposer.dispose(original)
  }

  private fun createLayer(view: JComponent) = JLayer(view, object : LayerUI<JComponent>() {

    override fun installUI(c: JComponent) {
      super.installUI(c)
      (c as JLayer<*>).layerEventMask = AWTEvent.MOUSE_MOTION_EVENT_MASK
    }

    override fun uninstallUI(c: JComponent) {
      super.uninstallUI(c)
      (c as JLayer<*>).layerEventMask = 0
    }

    override fun eventDispatched(e: AWTEvent, l: JLayer<out JComponent?>?) {
      if (e is MouseEvent) {

        val editorEx = original.editor as EditorEx
        val component = if (SwingUtilities.isDescendingFrom(e.component, editorEx.contentComponent)) {
          editorEx.contentComponent
        }
        else if (SwingUtilities.isDescendingFrom(e.component, editorEx.gutterComponentEx)) {
          editorEx.gutterComponentEx
        }
        else {
          null
        }
        if (component != null) {
          updateMouseOverCell(component, Point(SwingUtilities.convertPoint(e.component, e.point, component)))
        }
      }
    }

  })

  private fun updateMouseOverCell(component: JComponent, point: Point) {
    val cells = NotebookCellInlayManager.get(editor)!!.cells
    val currentOverCell = cells.filter { it.visible }.mapNotNull { it.view }.firstOrNull {
      val viewLeft = 0
      val viewTop = it.location.y
      val viewRight = component.size.width
      val viewBottom = viewTop + it.size.height
      viewLeft <= point.x && viewTop <= point.y && viewRight >= point.x && viewBottom >= point.y
    }
    if (mouseOverCell != currentOverCell) {
      mouseOverCell?.mouseExited()
      mouseOverCell = currentOverCell
      mouseOverCell?.mouseEntered()
    }
  }

}

fun decorateTextEditor(textEditor: TextEditor): TextEditor {
  return DecoratedEditor(textEditor)
}