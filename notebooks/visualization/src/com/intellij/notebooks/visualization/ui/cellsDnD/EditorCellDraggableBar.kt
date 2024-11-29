package com.intellij.notebooks.visualization.ui.cellsDnD

import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isOrdinaryNotebookEditor
import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.getCell
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeListener
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.EditorCellInput
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent
import javax.swing.SwingUtilities

class EditorCellDraggableBar(
  private val editor: EditorImpl,
  private val cellInput: EditorCellInput,
  private val yAndHeightSupplier: () -> Pair<Int, Int>,
) : Disposable {
  private var panel: JComponent? = null

  private val boundsChangeListener = object : JupyterBoundsChangeListener {
    override fun boundsChanged() = updateBounds()
  }

  init {
    if (Registry.`is`("jupyter.editor.dnd.cells")) createAndAddDraggableBar()
  }

  fun updateBounds() {
    val panel = panel ?: return
    val (y, height) = yAndHeightSupplier.invoke()
    // TODO: fix overlap with the run icon
    val x = editor.gutterComponentEx.iconAreaOffset
    panel.setBounds(x, y, DRAGGABLE_BAR_WIDTH, height)
  }

  private fun createAndAddDraggableBar() {
    if (!editor.isOrdinaryNotebookEditor()) return

    val panel = DraggableBarComponent()
    editor.gutterComponentEx.add(panel)
    this.panel = panel
    JupyterBoundsChangeHandler.get(editor).subscribe(boundsChangeListener)
    updateBounds()
  }

  override fun dispose() {
    panel?.let {
      editor.gutterComponentEx.apply {
        remove(it)
        repaint()
      }
      JupyterBoundsChangeHandler.Companion.get(editor).unsubscribe(boundsChangeListener)
      panel = null
    }
  }

  inner class DraggableBarComponent : JComponent() {
    private var isDragging = false
    private var dragStartPoint: Point? = null

    private var currentlyHighlightedCell: EditorCell? = null

    init {
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      isOpaque = false

      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          if (SwingUtilities.isLeftMouseButton(e)) {
            isDragging = true
            dragStartPoint = e.locationOnScreen
          }
        }

        override fun mouseReleased(e: MouseEvent) {
          if (!isDragging) return
          isDragging = false
          val dropLocation = e.locationOnScreen

          val editorLocationOnScreen = editor.contentComponent.locationOnScreen
          val x = dropLocation.x - editorLocationOnScreen.x
          val y = dropLocation.y - editorLocationOnScreen.y
          val editorPoint = Point(x, y)

          val targetCell = getCellUnderCursor(editorPoint)

          ApplicationManager.getApplication().messageBus
            .syncPublisher(CellDropNotifier.CELL_DROP_TOPIC)
            .cellDropped(CellDropEvent(cellInput.cell, targetCell))

          deleteDropIndicator()
        }
      })

      addMouseMotionListener(object : MouseMotionAdapter() {
        override fun mouseDragged(e: MouseEvent)  {
          if (!isDragging) return
          val currentLocation = e.locationOnScreen
          handleDrag(currentLocation)
        }
      })
    }

    private fun handleDrag(currentLocationOnScreen: Point) {
      val editorLocationOnScreen = editor.contentComponent.locationOnScreen
      val x = currentLocationOnScreen.x - editorLocationOnScreen.x
      val y = currentLocationOnScreen.y - editorLocationOnScreen.y

      val cellUnderCursor = getCellUnderCursor(Point(x, y))
      updateDropIndicator(cellUnderCursor)
    }

    private fun updateDropIndicator(targetCell: EditorCell?) {
      deleteDropIndicator()
      currentlyHighlightedCell = targetCell

      targetCell?.let {  it.view?.highlightAbovePanel() }
    }

    private fun deleteDropIndicator() = currentlyHighlightedCell?.let {
      it.view?.removeHighlightAbovePanel()
    }

    fun getCellUnderCursor(editorPoint: Point): EditorCell? {
      val offset = editor.xyToLogicalPosition(editorPoint).let { editor.logicalPositionToOffset(it) }
      val line = editor.document.getLineNumber(offset)
      return NotebookCellInlayManager.get(editor)?.getCell(editor.getCell(line).ordinal)
    }

  }

  companion object {
    private val DRAGGABLE_BAR_WIDTH = JBUI.scale(10)
  }
}