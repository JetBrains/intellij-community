package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.cellSelectionModel
import com.intellij.notebooks.visualization.getCells
import com.intellij.notebooks.visualization.ui.EditorLayerController.Companion.EDITOR_LAYER_CONTROLLER_KEY
import com.intellij.openapi.Disposable
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.removeUserData
import com.intellij.ui.ComponentUtil
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent
import javax.swing.JScrollPane
import kotlin.math.max
import kotlin.math.min

class DecoratedEditor private constructor(
  private val editorImpl: EditorImpl,
  private val manager: NotebookCellInlayManager,
) : NotebookEditor, Disposable.Default {
  override val hoveredCell: AtomicProperty<EditorCell?> = AtomicProperty(null)
  override val singleFileDiffMode: AtomicProperty<Boolean> = AtomicProperty(false)

  override val editorPositionKeeper: NotebookPositionKeeper = NotebookPositionKeeper(editorImpl).also {
    Disposer.register(this, it)
  }

  init {
    wrapEditorComponent(editorImpl)
    editorImpl.putUserData(NOTEBOOK_EDITOR_KEY, this)
  }

  private fun wrapEditorComponent(editor: EditorImpl) {
    val nestedScrollingSupport = NestedScrollingSupportImpl()

    val editorComponentWrapper = EditorComponentWrapper.install(editor)

    editorComponentWrapper.addEditorMouseMotionEvent(object : MouseMotionAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        nestedScrollingSupport.processMouseMotionEvent(e)
      }
    })

    editorComponentWrapper.addEditorMouseEventListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) = sendMouseEventToNestedScroll(e)
      override fun mouseReleased(e: MouseEvent) = sendMouseEventToNestedScroll(e)
      override fun mousePressed(e: MouseEvent) {
        sendMouseEventToNestedScroll(e)
        updateSelection(e)
      }

      private fun updateSelection(event: MouseEvent) {
        val point = NotebookUiUtils.getEditorPoint(editorImpl, event) ?: return

        val hoveredCell = manager.getCellByPoint(point) ?: return

        if (editorImpl.getMouseEventArea(event) != EditorMouseEventArea.EDITING_AREA) {
          editorImpl.setMode(NotebookEditorMode.COMMAND)
        }
        updateSelectionAfterClick(hoveredCell.interval, event.isCtrlPressed(), event.isShiftPressed(), event.button)
      }

      private fun sendMouseEventToNestedScroll(event: MouseEvent) {
        ComponentUtil.getParentOfType(JScrollPane::class.java, (event.component as? JComponent)
          ?.findComponentAt(event.point))
          ?.let { scrollPane ->
            nestedScrollingSupport.processMouseEvent(event, scrollPane)
          }
      }
    })

    editorComponentWrapper.addEditorMouseWheelEvent { nestedScrollingSupport.processMouseWheelEvent(it) }
  }

  override fun inlayClicked(clickedCell: NotebookCellLines.Interval, ctrlPressed: Boolean, shiftPressed: Boolean, mouseButton: Int) {
    editorImpl.setMode(NotebookEditorMode.COMMAND)
    updateSelectionAfterClick(clickedCell, ctrlPressed, shiftPressed, mouseButton)
  }

  fun updateSelectionAfterClick(clickedCell: NotebookCellLines.Interval, ctrlPressed: Boolean, shiftPressed: Boolean, mouseButton: Int) {
    val model = editorImpl.cellSelectionModel!!
    when {
      ctrlPressed -> {
        if (model.isSelectedCell(clickedCell)) {
          model.removeSelection(clickedCell)
        }
        else {
          model.selectCell(clickedCell, makePrimary = true)
        }
      }
      shiftPressed -> {
        // select or deselect all cells from primary to the selected one
        val primaryCell = model.primarySelectedCell
        val line1 = primaryCell.lines.first
        val line2 = clickedCell.lines.first
        val range = IntRange(min(line1, line2), max(line1, line2))

        val cellsInRange = editorImpl.getCells(range)
        val affectedSelectedCells = model.selectedRegions.filter { hasIntersection(it, cellsInRange) }.flatten()

        if (affectedSelectedCells == cellsInRange) {
          for (cell in (cellsInRange - primaryCell)) {
            model.removeSelection(cell)
          }
        }
        else {
          for (cell in (affectedSelectedCells - cellsInRange)) {
            model.removeSelection(cell)
          }
          for (cell in (cellsInRange - affectedSelectedCells)) {
            model.selectCell(cell)
          }
        }
      }
      mouseButton == MouseEvent.BUTTON1 && !model.isSelectedCell(clickedCell) -> {
        model.selectSingleCell(clickedCell)
      }
    }
  }

  companion object {
    fun install(original: EditorImpl, manager: NotebookCellInlayManager) {
      val decoratedEditor = DecoratedEditor(original, manager)
      val controller = EditorLayerController(
        decoratedEditor.editorImpl.scrollPane.viewport.view as EditorComponentWrapper
      )
      original.putUserData(EDITOR_LAYER_CONTROLLER_KEY, controller)

      Disposer.register(original.disposable, decoratedEditor)
      Disposer.register(original.disposable) {
        original.removeUserData(EDITOR_LAYER_CONTROLLER_KEY)
      }
    }
  }
}

/** lists assumed to be ordered and non-empty  */
private fun hasIntersection(cells: List<NotebookCellLines.Interval>, others: List<NotebookCellLines.Interval>): Boolean =
  !(cells.last().ordinal < others.first().ordinal || cells.first().ordinal > others.last().ordinal)

private fun MouseEvent.isCtrlPressed(): Boolean =
  (modifiersEx and if (ClientSystemInfo.isMac()) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK) != 0

private fun MouseEvent.isShiftPressed(): Boolean =
  (modifiersEx and InputEvent.SHIFT_DOWN_MASK) != 0