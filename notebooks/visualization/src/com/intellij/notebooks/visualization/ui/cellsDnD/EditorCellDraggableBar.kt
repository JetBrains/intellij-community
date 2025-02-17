package com.intellij.notebooks.visualization.ui.cellsDnD

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.getCell
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeListener
import com.intellij.notebooks.visualization.ui.EditorCellInput
import com.intellij.notebooks.visualization.ui.computeFirstLineForHighlighter
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.Nls
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
  private val foldInput: () -> Unit,
  private val unfoldInput: () -> Unit,
) : Disposable {
  private var panel: DraggableBarComponent? = null

  private val boundsChangeListener = object : JupyterBoundsChangeListener {
    override fun boundsChanged() = updateBounds()
  }

  private val inlayManager = NotebookCellInlayManager.get(editor)

  init {
    JupyterBoundsChangeHandler.get(editor).subscribe(boundsChangeListener)
  }

  var visible: Boolean = false
    set(value) {
      if (value) createAndAddDraggableBar()
      else removeDraggableBar()
      field = value
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

      val firstLine = cellInput.interval.computeFirstLineForHighlighter(editor)
      val y = editor.logicalPositionToXY(LogicalPosition(firstLine, 0)).y + editor.lineHeight
      val height = lowerInlayBounds.y + lowerInlayBounds.height - y

      it.setBounds(x, y, width, height)
    }
  }

  private fun createAndAddDraggableBar() {
    val panel = DraggableBarComponent()
    editor.gutterComponentEx.add(panel)
    editor.gutterComponentEx.setComponentZOrder(panel, 0)
    this.panel = panel
    updateBounds()
  }

  private fun removeDraggableBar() {
    panel?.let {
      editor.gutterComponentEx.remove(it)
      panel = null
    }
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
    private var dragPreview: CellDragCellPreviewWindow? = null

    private var wasFolded: Boolean = false
    private var inputFoldedState: Boolean = false
    private var outputInitialStates: MutableMap<Int, Boolean> = mutableMapOf()

    init {
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      isOpaque = true

      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          if (SwingUtilities.isLeftMouseButton(e)) {
            isDragging = true
            dragStartPoint = e.locationOnScreen
          }
        }

        override fun mouseReleased(e: MouseEvent) {
          deleteDragPreview()
          if (!isDragging || dragStartPoint == null) {
            isDragging = false
            return
          }

          val dragDistance = e.locationOnScreen.distance(dragStartPoint!!)
          if (dragDistance < 5) {
            clearDragState()
            unfoldCellIfNeeded()
            return
          }

          clearDragState()
          val targetCell = retrieveTargetCell(e)
          unfoldCellIfNeeded()

          ApplicationManager.getApplication().messageBus
            .syncPublisher(CellDropNotifier.getTopicForEditor(editor))
            .cellDropped(CellDropEvent(cellInput.cell, targetCell))
        }

      })

      addMouseMotionListener(object : MouseMotionAdapter() {
        override fun mouseDragged(e: MouseEvent)  {
          if (!isDragging) return

          if (dragPreview == null) {
            dragPreview = CellDragCellPreviewWindow(getPlaceholderText(), editor)
            dragPreview?.isVisible = true
            foldDraggedCell()
          }

          dragPreview?.followCursor(e.locationOnScreen)
          val currentLocation = e.locationOnScreen
          handleDrag(currentLocation)
        }
      })
    }

    fun getCellUnderCursor(editorPoint: Point): CellDropTarget {
      val notebookCellManager = NotebookCellInlayManager.get(editor) ?: return CellDropTarget.NoCell

      notebookCellManager.getCell(notebookCellManager.cells.lastIndex).view?.calculateBounds()?.let { lastCellBounds ->
        if (editorPoint.y > lastCellBounds.maxY) return CellDropTarget.BelowLastCell
      }

      val line = editor.document.getLineNumber(editor.xyToLogicalPosition(editorPoint).let(editor::logicalPositionToOffset))
      val realCell = notebookCellManager.getCell(editor.getCell(line).ordinal)
      return CellDropTarget.TargetCell(realCell)
    }

    private fun retrieveTargetCell(e: MouseEvent): CellDropTarget {
      val dropLocation = e.locationOnScreen
      val editorLocationOnScreen = editor.contentComponent.locationOnScreen
      val x = dropLocation.x - editorLocationOnScreen.x
      val y = dropLocation.y - editorLocationOnScreen.y
      val editorPoint = Point(x, y)

      return getCellUnderCursor(editorPoint)
    }

    private fun foldDraggedCell() {
      inputFoldedState = cellInput.folded
      foldInput()

      cellInput.cell.view?.outputs?.outputs?.forEachIndexed { index, output ->
        outputInitialStates[index] = output.collapsed
        output.collapsed = true
      }
      wasFolded = true
    }

    private fun unfoldCellIfNeeded() {
      if (wasFolded == false) return
      if (inputFoldedState == false) unfoldInput()

      cellInput.cell.view?.outputs?.outputs?.forEachIndexed { index, output ->
        output.collapsed = outputInitialStates[index] == true
      }
      outputInitialStates.clear()
      wasFolded = false
    }

    private fun clearDragState() {
      isDragging = false
      deleteDropIndicator()
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
        is CellDropTarget.TargetCell -> targetCell.cell.view?.addDropHighlightIfApplicable()
        CellDropTarget.BelowLastCell -> addHighlightAfterLastCell()
        else -> { }
      }
    }

    private fun deleteDropIndicator() = when(currentlyHighlightedCell) {
      is CellDropTarget.TargetCell -> (currentlyHighlightedCell as CellDropTarget.TargetCell).cell.view?.removeDropHighlightIfPresent()
      CellDropTarget.BelowLastCell -> removeHighlightAfterLastCell()
      else -> { }
    }

    private fun deleteDragPreview() {
      dragPreview?.dispose()
      dragPreview = null
    }

    @Nls
    private fun getPlaceholderText(): String {
      @NlsSafe val firstNotEmptyString = cellInput.cell.source.get ().lines().firstOrNull { it.trim().isNotEmpty() }
      return StringUtil.shortenTextWithEllipsis(firstNotEmptyString ?: "\u2026", 20, 0)
    }

    private fun addHighlightAfterLastCell() = inlayManager?.belowLastCellPanel?.addDropHighlight()

    private fun removeHighlightAfterLastCell() = inlayManager?.belowLastCellPanel?.removeDropHighlight()

  }

}