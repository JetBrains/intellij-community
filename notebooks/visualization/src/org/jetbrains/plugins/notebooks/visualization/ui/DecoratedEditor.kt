package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager.FullEditorWidthRenderer
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayManager
import org.jetbrains.plugins.notebooks.visualization.ui.EditorCellViewEventListener.CellViewRemoved
import org.jetbrains.plugins.notebooks.visualization.ui.EditorCellViewEventListener.EditorCellViewEvent
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLayer
import javax.swing.SwingUtilities
import javax.swing.plaf.LayerUI

private class DecoratedEditor(private val original: TextEditor, private val manager: NotebookCellInlayManager) : TextEditor by original {

  private var mouseOverCell: EditorCellView? = null

  private val component = NestedScrollingSupport.addNestedScrollingSupport(createLayer(original.component))

  init {
    if (!GraphicsEnvironment.isHeadless()) {
      setupScrollPaneListener()
    }

    setupEditorComponentWrapper()

    manager.onInvalidate {
      component.revalidate()
    }
    manager.addCellViewEventsListener(object : EditorCellViewEventListener {
      override fun onEditorCellViewEvents(events: List<EditorCellViewEvent>) {
        events.asSequence().filterIsInstance<CellViewRemoved>().forEach {
          if (it.view == mouseOverCell) {
            mouseOverCell = null
          }
        }
      }
    }, this)
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

  override fun getStructureViewBuilder(): StructureViewBuilder? {
    return original.structureViewBuilder
  }

  private fun setupEditorComponentWrapper() {
    val editorEx = original.editor as EditorEx
    val scrollPane = editorEx.scrollPane
    val view = scrollPane.viewport.view
    scrollPane.viewport.isOpaque = false
    scrollPane.viewport.view = EditorComponentWrapper(editorEx, view as EditorComponentImpl, manager)
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
          updateMouseOverCell(component, SwingUtilities.convertPoint(e.component, e.point, component))
        }
      }
    }
  })

  private fun updateMouseOverCell(component: JComponent, point: Point) {
    val cells = manager.cells
    val currentOverCell = cells.filter { it.visible }.mapNotNull { it.view }.firstOrNull {
      val viewLeft = 0
      val viewTop = it.bounds.y
      val viewRight = component.size.width
      val viewBottom = viewTop + it.bounds.height
      viewLeft <= point.x && viewTop <= point.y && viewRight >= point.x && viewBottom >= point.y
    }

    if (mouseOverCell != currentOverCell) {
      mouseOverCell?.mouseExited()
      mouseOverCell = currentOverCell
      mouseOverCell?.mouseEntered()
    }
  }
}

fun decorateTextEditor(textEditor: TextEditor, manager: NotebookCellInlayManager): TextEditor {
  return DecoratedEditor(textEditor, manager)
}

internal fun keepScrollingPositionWhile(editor: Editor, task: Runnable) {
  ReadAction.run<Nothing> {
    EditorScrollingPositionKeeper(editor).use { keeper ->
      keeper.savePosition()
      task.run()
      keeper.restorePosition(false)
    }
  }
}

class EditorComponentWrapper(
  private val editor: Editor,
  private val editorComponent: EditorComponentImpl,
  private val manager: NotebookCellInlayManager,
) : JComponent() {
  init {
    isOpaque = false
    layout = BorderLayout()
    add(editorComponent, BorderLayout.CENTER)
  }

  override fun doLayout() {
    super.doLayout()
    // EditorEmbeddedComponentManager breaks the Swing layout model as it expect that subcomponents will define their own bounds themselves.
    // Here we invoke FullEditorWidthRenderer#validate to place inlay components correctly after doLayout.
    editorComponent.components.asSequence()
      .filterIsInstance<FullEditorWidthRenderer>()
      .forEach {
        it.validate()
      }
    manager.validateCells()
  }

  override fun validateTree() {
    keepScrollingPositionWhile(editor) {
      super.validateTree()
    }
  }
}