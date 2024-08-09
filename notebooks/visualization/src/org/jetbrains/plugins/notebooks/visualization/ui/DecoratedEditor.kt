package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.setMode
import org.jetbrains.plugins.notebooks.visualization.*
import org.jetbrains.plugins.notebooks.visualization.ui.EditorCellViewEventListener.CellViewRemoved
import org.jetbrains.plugins.notebooks.visualization.ui.EditorCellViewEventListener.EditorCellViewEvent
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JLayer
import javax.swing.SwingUtilities
import javax.swing.plaf.LayerUI
import kotlin.math.max
import kotlin.math.min

private class DecoratedEditor(private val original: TextEditor, private val manager: NotebookCellInlayManager) : TextEditor by original, NotebookEditor {

  private var mouseOverCell: EditorCellView? = null

  private val component = NestedScrollingSupport.addNestedScrollingSupport(createLayer(original.component))

  private val selectionModel = EditorCellSelectionModel(manager)

  private var selectionUpdateScheduled: AtomicBoolean = AtomicBoolean(false)

  init {
    if (!GraphicsEnvironment.isHeadless()) {
      setupScrollPane()
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
    original.editor.addEditorMouseListener(object : EditorMouseListener {
      override fun mousePressed(event: EditorMouseEvent) {
        if (!event.isConsumed && event.mouseEvent.button == MouseEvent.BUTTON1) {
          val point = getEditorPoint(event.mouseEvent)?.second ?: return

          val selectedCell = getCellViewByPoint(point)?.cell ?: return

          if (event.area == EditorMouseEventArea.EDITING_AREA && event.inlay == null && event.collapsedFoldRegion == null) {
            editor.setMode(NotebookEditorMode.EDIT)
          }
          else {
            editor.setMode(NotebookEditorMode.COMMAND)
          }
          if (event.area != EditorMouseEventArea.EDITING_AREA)
            mousePressed(selectedCell.interval, event.isCtrlPressed(), event.isShiftPressed())

        }
      }
    }, this)

    editor.caretModel.addCaretListener(object : CaretListener {
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

    notebookEditorKey.set(original.editor, this)
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
      editor.caretModel.allCarets.flatMap { getCellsByCaretSelection(it) }
    )
  }

  private fun getCellsByCaretSelection(caret: Caret): List<EditorCell> {
    val lines = editor.document.getSelectionLines(caret)
    return manager.cells.filter { it.interval.lines.hasIntersectionWith(lines) }
  }

  private fun setupScrollPane() {
    val editorEx = original.editor as EditorEx
    val scrollPane = editorEx.scrollPane
    editorEx.scrollPane.viewport.isOpaque = false
    scrollPane.viewport.addChangeListener {
      editorEx.contentComponent.mousePosition?.let {
        updateMouseOverCell(it)
      }
      editorEx.gutterComponentEx.mousePosition?.let {
        updateMouseOverCell(it)
      }
    }
  }

  override fun getStructureViewBuilder(): StructureViewBuilder? {
    return original.structureViewBuilder
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
        getEditorPoint(e)?.let { (_, point) ->
          updateMouseOverCell(point)
        }
      }
    }
  })

  private fun getEditorPoint(e: MouseEvent): Pair<Component, Point>? {
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
    val visualLine = editor.xyToLogicalPosition(point)
    val cur = manager.cells.firstOrNull { it.interval.lines.contains(visualLine.line) }
    return cur?.view
  }

  override fun inlayClicked(clickedCell: NotebookCellLines.Interval, ctrlPressed: Boolean, shiftPressed: Boolean) {
    editor.setMode(NotebookEditorMode.COMMAND)
    mousePressed(clickedCell, ctrlPressed, shiftPressed)
  }

  @Suppress("ConvertArgumentToSet")
  private fun mousePressed(clickedCell: NotebookCellLines.Interval, ctrlPressed: Boolean, shiftPressed: Boolean) {
    val model = editor.cellSelectionModel!!
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

        val cellsInRange = editor.getCells(range)
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

}

fun decorateTextEditor(textEditor: TextEditor, manager: NotebookCellInlayManager): TextEditor {
  return DecoratedEditor(textEditor, manager)
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