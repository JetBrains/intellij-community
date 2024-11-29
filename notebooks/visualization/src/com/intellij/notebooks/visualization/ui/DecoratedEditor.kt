package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.notebooks.visualization.*
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.ui.EditorCellViewEventListener.CellViewRemoved
import com.intellij.notebooks.visualization.ui.EditorCellViewEventListener.EditorCellViewEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.ui.ComponentUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import kotlin.math.max
import kotlin.math.min

class DecoratedEditor private constructor(
  private val editorImpl: EditorImpl,
  private val manager: NotebookCellInlayManager,
) : NotebookEditor {

  /** Used to hold current cell under mouse, to update the folding state and "run" button state. */
  private var mouseOverCell: EditorCellView? = null

  private val selectionModel = EditorCellSelectionModel(manager)

  private var selectionUpdateScheduled = AtomicBoolean(false)

  init {
    if (!GraphicsEnvironment.isHeadless()) {
      setupScrollPane()
    }

    wrapEditorComponent(editorImpl)

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
      override fun caretAdded(event: CaretEvent) = scheduleSelectionUpdate()
      override fun caretPositionChanged(event: CaretEvent) = scheduleSelectionUpdate()
      override fun caretRemoved(event: CaretEvent) = scheduleSelectionUpdate()
    })

    updateSelectionByCarets()

    notebookEditorKey.set(editorImpl, this)
  }

  private fun wrapEditorComponent(editor: EditorImpl) {
    val nestedScrollingSupport = NestedScrollingSupportImpl()

    NotebookAWTMouseDispatcher(editor.scrollPane).apply {

      eventDispatcher.addListener { event ->
        if (event is MouseEvent) {
          getEditorPoint(event)?.let { (_, point) ->
            updateMouseOverCell(point)
          }
        }
      }

      eventDispatcher.addListener { event ->
        if (event is MouseWheelEvent) {
          nestedScrollingSupport.processMouseWheelEvent(event)
        }
        else if (event is MouseEvent) {
          if (event.id == MouseEvent.MOUSE_CLICKED || event.id == MouseEvent.MOUSE_RELEASED || event.id == MouseEvent.MOUSE_PRESSED) {
            ComponentUtil.getParentOfType(JScrollPane::class.java, (event.component as? JComponent)
              ?.findComponentAt(event.point))
              ?.let { scrollPane ->
                nestedScrollingSupport.processMouseEvent(event, scrollPane)
              }
          }
          else if (event.id == MouseEvent.MOUSE_MOVED) {
            nestedScrollingSupport.processMouseMotionEvent(event)
          }
        }
      }

      Disposer.register(editor.disposable, this)
    }

    editor.scrollPane.viewport.view = EditorComponentWrapper(editor, editor.scrollPane.viewport, editor.contentComponent)
  }

  /** The main thing while we need it - to perform updating of underlying components within keepScrollingPositionWhile. */
  private class EditorComponentWrapper(private val editor: Editor, private val editorViewport: JViewport, component: Component) : JPanel(BorderLayout()) {
    init {
      isOpaque = false
      // The reason why we need to wrap into fate viewport is the code in [com/intellij/openapi/editor/impl/EditorImpl.java:2031]
      //     Rectangle rect = ((JViewport)myEditorComponent.getParent()).getViewRect();
      // There is expected that the parent of myEditorComponent will be not EditorComponentWrapper, but JViewport.
      add(object : JViewport() {
        override fun getViewRect() = editorViewport.viewRect
      }.apply {
        view = component
      }, BorderLayout.CENTER)
    }

    override fun validateTree() {
      keepScrollingPositionWhile(editor) {
        JupyterBoundsChangeHandler.get(editor).postponeUpdates()
        super.validateTree()
        JupyterBoundsChangeHandler.get(editor).performPostponed()
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
  return WriteIntentReadAction.compute<T, Nothing> {
    EditorScrollingPositionKeeper(editor).use { keeper ->
      if (editor.isDisposed) {
        return@compute task()
      }
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