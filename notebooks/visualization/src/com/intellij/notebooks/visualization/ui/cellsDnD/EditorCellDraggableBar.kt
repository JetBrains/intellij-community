package com.intellij.notebooks.visualization.ui.cellsDnD

import com.intellij.icons.AllIcons
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isOrdinaryNotebookEditor
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.getCell
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeListener
import com.intellij.notebooks.visualization.ui.EditorCellInput
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.registry.Registry
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent
import javax.swing.SwingUtilities

class EditorCellDraggableBar(
  private val editor: EditorImpl,
  private val cellInput: EditorCellInput,
) : Disposable {
  private var panel: JComponent? = null

  private val boundsChangeListener = object : JupyterBoundsChangeListener {
    override fun boundsChanged() = updateBounds()
  }

  private val dragIcon = AllIcons.General.Drag

  private val inlayManager = NotebookCellInlayManager.get(editor)

  init {
    if (Registry.`is`("jupyter.editor.dnd.cells")) createAndAddDraggableBar()
  }

  fun updateBounds() {
    panel?.let {
      val inlays = cellInput.getBlockElementsInRange()

      val lowerInlayBounds = inlays.lastOrNull {
        it.properties.isShownAbove == false &&
        it.properties.priority == editor.notebookAppearance.JUPYTER_CELL_SPACERS_INLAY_PRIORITY
      }?.bounds ?: return@let

      val x = editor.gutterComponentEx.iconAreaOffset
      val width = editor.gutterComponentEx.getIconsAreaWidth()

      val y = lowerInlayBounds.y
      val height = lowerInlayBounds.height

      it.setBounds(x, y, width, height)
    }
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

    private var currentlyHighlightedCell: CellDropTarget = CellDropTarget.NoCell

    init {
      cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)  // todo: need to find a better cursor
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

    fun getCellUnderCursor(editorPoint: Point): CellDropTarget {
      val notebookCellManager = NotebookCellInlayManager.get(editor) ?: return CellDropTarget.NoCell

      // Check if the point is below the bounds of the last cell
      notebookCellManager.getCell(notebookCellManager.cells.lastIndex).view?.calculateBounds()?.let { lastCellBounds ->
        if (editorPoint.y > lastCellBounds.maxY) return CellDropTarget.BelowLastCell
      }

      val line = editor.document.getLineNumber(editor.xyToLogicalPosition(editorPoint).let(editor::logicalPositionToOffset))
      val realCell = notebookCellManager.getCell(editor.getCell(line).ordinal)
      return CellDropTarget.TargetCell(realCell)
    }

    override fun paintComponent(g: Graphics?) {
      super.paintComponent(g)
      val g2d = g as Graphics2D

      // todo: deal with empty lower panels - in such case, icon does not fit
      val iconX = (width - dragIcon.iconWidth) / 2
      val iconY = (height - dragIcon.iconHeight) / 2
      dragIcon.paintIcon(this, g2d, iconX, iconY)
    }

    private fun handleDrag(currentLocationOnScreen: Point) {
      val editorLocationOnScreen = editor.contentComponent.locationOnScreen
      val x = currentLocationOnScreen.x - editorLocationOnScreen.x
      val y = currentLocationOnScreen.y - editorLocationOnScreen.y

      val cellUnderCursor = getCellUnderCursor(Point(x, y))
      updateDropIndicator(cellUnderCursor)
    }

    private fun updateDropIndicator(targetCell: CellDropTarget) {
      deleteDropIndicator()
      currentlyHighlightedCell = targetCell

      when (targetCell) {
        is CellDropTarget.TargetCell -> targetCell.cell.view?.highlightAbovePanel()
        CellDropTarget.BelowLastCell -> addHighlightAfterLastCell()
        else -> { }
      }
    }

    private fun deleteDropIndicator() = when(currentlyHighlightedCell) {
      is CellDropTarget.TargetCell -> (currentlyHighlightedCell as CellDropTarget.TargetCell).cell.view?.removeHighlightAbovePanel()
      CellDropTarget.BelowLastCell -> removeHighlightAfterLastCell()
      else -> { }
    }

    private fun addHighlightAfterLastCell() = inlayManager?.belowLastCellPanel?.addDropHighlight()

    private fun removeHighlightAfterLastCell() = inlayManager?.belowLastCellPanel?.removeDropHighlight()

  }

}