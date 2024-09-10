package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.notebooks.visualization.*
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.ui.EditorCellViewEventListener.CellViewRemoved
import com.intellij.notebooks.visualization.ui.EditorCellViewEventListener.EditorCellViewEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorComponent
import com.intellij.openapi.util.use
import com.intellij.ui.AncestorListenerAdapter
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JLayer
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.AncestorEvent
import javax.swing.plaf.LayerUI
import kotlin.math.max
import kotlin.math.min

class DecoratedEditor private constructor(private val editorImpl: EditorImpl, private val manager: NotebookCellInlayManager) : NotebookEditor {

  /** Used to hold current cell under mouse, to update the folding state and "run" button state. */
  private var mouseOverCell: EditorCellView? = null

  private val selectionModel = EditorCellSelectionModel(manager)

  private var selectionUpdateScheduled: AtomicBoolean = AtomicBoolean(false)

  /**
   * Correct parent for the editor component - our special scroll-supporting layer.
   * We cannot wrap editorComponent at creation, so we are changing its parent du
   */
  private var editorComponentParent: JLayer<JComponent>? = null

  init {
    if (!GraphicsEnvironment.isHeadless()) {
      setupScrollPane()
    }

    wrapEditorComponent(editorImpl)
    editorImpl.component.addAncestorListener(object : AncestorListenerAdapter() {
      override fun ancestorAdded(event: AncestorEvent?) {
        wrapEditorComponent(editorImpl)
      }
    })

    manager.addCellViewEventsListener(object : EditorCellViewEventListener {
      override fun onEditorCellViewEvents(events: List<EditorCellViewEvent>) {
        events.asSequence().filterIsInstance<CellViewRemoved>().forEach {
          if (it.view == mouseOverCell) {
            mouseOverCell = null
          }
        }
      }
    }, editorImpl.disposable)

    editorImpl.addEditorMouseListener(object : EditorMouseListener {
      override fun mousePressed(event: EditorMouseEvent) {
        if (!event.isConsumed && event.mouseEvent.button == MouseEvent.BUTTON1) {
          val point = getEditorPoint(event.mouseEvent)?.second ?: return

          val selectedCell = getCellViewByPoint(point)?.cell ?: return

          if (event.area != EditorMouseEventArea.EDITING_AREA) {
            mousePressed(selectedCell.interval, event.isCtrlPressed(), event.isShiftPressed())
          }
        }
      }
    }, editorImpl.disposable)

    editorImpl.caretModel.addCaretListener(object : CaretListener {
      override fun caretAdded(event: CaretEvent) {
        scheduleSelectionUpdate()
      }

      override fun caretPositionChanged(event: CaretEvent) {
        scheduleSelectionUpdate()
      }

      override fun caretRemoved(event: CaretEvent) {
        scheduleSelectionUpdate()
      }
    })

    updateSelectionByCarets()

    notebookEditorKey.set(editorImpl, this)
  }

  private fun wrapEditorComponent(editor: EditorImpl) {
    val parent = editor.component.parent
    if (parent == null || parent == editorComponentParent) {
      return
    }

    val view = editorImpl.scrollPane.viewport.view
    if (view is EditorComponentImpl) {
      editorImpl.scrollPane.viewport.view = EditorComponentWrapper(editorImpl, view)
    }

    editorComponentParent = createCellUnderMouseSupportLayer(editorImpl.component)
    val secondLayer = NestedScrollingSupport.addNestedScrollingSupport(editorComponentParent!!)

    parent.remove(editor.component)
    val newComponent = secondLayer

    if (parent is TextEditorComponent) {
      parent.__add(newComponent, GridBagConstraints().also {
        it.gridx = 0
        it.gridy = 0
        it.weightx = 1.0
        it.weighty = 1.0
        it.fill = GridBagConstraints.BOTH
      })
    }
    else {
      parent.add(newComponent, BorderLayout.CENTER)
    }
  }

  /** The main thing while we need it - to perform updating of underlying components within keepScrollingPositionWhile. */
  private class EditorComponentWrapper(private val editor: Editor, component: Component) : JPanel(BorderLayout()) {
    init {
      isOpaque = false
      add(component, BorderLayout.CENTER)
    }

    override fun validateTree() {
      keepScrollingPositionWhile(editor) {
        JupyterBoundsChangeHandler.get(editor)?.postponeUpdates()
        super.validateTree()
        JupyterBoundsChangeHandler.get(editor)?.performPostponed()
      }
    }
  }

  private fun scheduleSelectionUpdate() {
    if (selectionUpdateScheduled.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater {
        try {
          updateSelectionByCarets()
        }
        finally {
          selectionUpdateScheduled.set(false)
        }
      }
    }
  }

  private fun updateSelectionByCarets() {
    selectionModel.replaceSelection(
      editorImpl.caretModel.allCarets.flatMap { getCellsByCaretSelection(it) }
    )
  }

  private fun getCellsByCaretSelection(caret: Caret): List<EditorCell> {
    val lines = editorImpl.document.getSelectionLines(caret)
    return manager.cells.filter { it.interval.lines.hasIntersectionWith(lines) }
  }

  private fun setupScrollPane() {
    val scrollPane = editorImpl.scrollPane
    editorImpl.scrollPane.viewport.isOpaque = false
    scrollPane.viewport.addChangeListener {
      editorImpl.contentComponent.mousePosition?.let {
        updateMouseOverCell(it)
      }
      editorImpl.gutterComponentEx.mousePosition?.let {
        updateMouseOverCell(it)
      }
    }
  }

  private fun createCellUnderMouseSupportLayer(view: JComponent) = JLayer(view, object : LayerUI<JComponent>() {

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
        getEditorPoint(e)?.let { (_, point) ->
          updateMouseOverCell(point)
        }
      }
    }
  })

  private fun getEditorPoint(e: MouseEvent): Pair<Component, Point>? {
    val component = if (SwingUtilities.isDescendingFrom(e.component, editorImpl.contentComponent)) {
      editorImpl.contentComponent
    }
    else if (SwingUtilities.isDescendingFrom(e.component, editorImpl.gutterComponentEx)) {
      editorImpl.gutterComponentEx
    }
    else {
      null
    }
    return if (component != null) {
      component to SwingUtilities.convertPoint(e.component, e.point, component)
    }
    else {
      null
    }
  }

  private fun updateMouseOverCell(point: Point) {
    val currentOverCell = getCellViewByPoint(point)

    if (mouseOverCell != currentOverCell) {
      mouseOverCell?.mouseExited()
      mouseOverCell = currentOverCell
      mouseOverCell?.mouseEntered()
    }
  }

  private fun getCellViewByPoint(point: Point): EditorCellView? {
    val visualLine = editorImpl.xyToLogicalPosition(point)
    val cur = manager.cells.firstOrNull { it.interval.lines.contains(visualLine.line) }
    return cur?.view
  }

  override fun inlayClicked(clickedCell: NotebookCellLines.Interval, ctrlPressed: Boolean, shiftPressed: Boolean) {
    mousePressed(clickedCell, ctrlPressed, shiftPressed)
  }

  private fun mousePressed(clickedCell: NotebookCellLines.Interval, ctrlPressed: Boolean, shiftPressed: Boolean) {
    editorImpl.setMode(NotebookEditorMode.COMMAND)
    updateSelectionAfterClick(clickedCell, ctrlPressed, shiftPressed)
  }

  @Suppress("ConvertArgumentToSet")
  private fun updateSelectionAfterClick(clickedCell: NotebookCellLines.Interval, ctrlPressed: Boolean, shiftPressed: Boolean) {
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
        // select or deselect all cells from primary to selected
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
      else -> {
        model.selectSingleCell(clickedCell)
      }
    }
  }

  companion object {
    fun install(original: EditorImpl, manager: NotebookCellInlayManager) {
      DecoratedEditor(original, manager)
    }
  }
}

internal fun <T> keepScrollingPositionWhile(editor: Editor, task: () -> T): T {
  return ReadAction.compute<T, Nothing> {
    EditorScrollingPositionKeeper(editor).use { keeper ->
      keeper.savePosition()
      val r = task()
      keeper.restorePosition(false)
      r
    }
  }
}

/** lists assumed to be ordered and non-empty  */
private fun hasIntersection(cells: List<NotebookCellLines.Interval>, others: List<NotebookCellLines.Interval>): Boolean =
  !(cells.last().ordinal < others.first().ordinal || cells.first().ordinal > others.last().ordinal)

private fun EditorMouseEvent.isCtrlPressed(): Boolean =
  (mouseEvent.modifiersEx and if (ClientSystemInfo.isMac()) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK) != 0

private fun EditorMouseEvent.isShiftPressed(): Boolean =
  (mouseEvent.modifiersEx and InputEvent.SHIFT_DOWN_MASK) != 0