package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.notebooks.visualization.*
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.ui.EditorCellViewEventListener.CellViewRemoved
import com.intellij.notebooks.visualization.ui.EditorCellViewEventListener.EditorCellViewEvent
import com.intellij.notebooks.visualization.ui.EditorLayerController.Companion.EDITOR_LAYER_CONTROLLER_KEY
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.removeUserData
import com.intellij.ui.ComponentUtil
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.MOUSE_PRESSED
import java.awt.event.MouseWheelEvent
import java.awt.geom.Line2D
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.plaf.LayerUI
import kotlin.math.max
import kotlin.math.min

class DecoratedEditor private constructor(
  private val editorImpl: EditorImpl,
  private val manager: NotebookCellInlayManager,
) : NotebookEditor, Disposable {

  /** Used to hold current cell under mouse, to update the folding state and "run" button state. */
  override var mouseOverCell: EditorCellView? = null
    private set

  override val editorPositionKeeper: NotebookPositionKeeper = NotebookPositionKeeper(editorImpl).also {
    Disposer.register(this, it)
  }

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

    editorImpl.caretModel.addCaretListener(object : CaretListener {
      override fun caretAdded(event: CaretEvent) = scheduleSelectionUpdate()
      override fun caretPositionChanged(event: CaretEvent) = scheduleSelectionUpdate()
      override fun caretRemoved(event: CaretEvent) = scheduleSelectionUpdate()
    })

    updateSelectionByCarets()

    notebookEditorKey.set(editorImpl, this)
  }

  override fun dispose() {
    mouseOverCell = null
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
          if (event.id == MouseEvent.MOUSE_CLICKED || event.id == MouseEvent.MOUSE_RELEASED || event.id == MOUSE_PRESSED) {
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

      eventDispatcher.addListener { event ->
        if (event.id == MOUSE_PRESSED && event is MouseEvent) {
          val point = getEditorPoint(event)?.second ?: return@addListener

          val selectedCell = getCellViewByPoint(point)?.cell ?: return@addListener

          if (editorImpl.getMouseEventArea(event) != EditorMouseEventArea.EDITING_AREA) {
            editorImpl.setMode(NotebookEditorMode.COMMAND)
          }
          updateSelectionAfterClick(selectedCell.interval, event.isCtrlPressed(), event.isShiftPressed(), event.button)
        }
      }

      Disposer.register(editor.disposable, this)
    }

    editor.scrollPane.viewport.view = EditorComponentWrapper(editor, editor.scrollPane.viewport, editor.contentComponent)
  }

  /** The main thing while we need it - to perform updating of underlying components within keepScrollingPositionWhile. */
  class EditorComponentWrapper(
    private val editor: Editor,
    private val editorViewport: JViewport,
    component: Component,
  ) : JPanel(BorderLayout()) {
    private val layeredPane: JLayer<JPanel>
    private val overlayLines = mutableListOf<Pair<Line2D, Color>>()

    init {
      isOpaque = false

      val editorPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        val viewportWrapper = object : JViewport() {
          override fun getViewRect() = editorViewport.viewRect
        }
        viewportWrapper.view = component
        add(viewportWrapper, BorderLayout.CENTER)
      }

      layeredPane = JLayer(editorPanel).apply {
        setUI(object : LayerUI<JPanel>() {
          override fun paint(graphics: Graphics, component: JComponent) {
            super.paint(graphics, component)

            val g2d = graphics.create() as Graphics2D
            try {
              for ((line, color) in overlayLines) {
                g2d.color = color
                g2d.draw(line)
              }
            } finally {
              g2d.dispose()
            }
          }
        })
      }

      add(layeredPane, BorderLayout.CENTER)
    }

    override fun validateTree() {
      editor.notebookEditor.editorPositionKeeper.keepScrollingPositionWhile {
        JupyterBoundsChangeHandler.get(editor).postponeUpdates()
        super.validateTree()
        JupyterBoundsChangeHandler.get(editor).schedulePerformPostponed()
      }
    }

    fun addOverlayLine(line: Line2D, color: Color) {
      overlayLines.add(line to color)
      layeredPane.repaint()
    }

    fun removeOverlayLine(line: Line2D) {
      overlayLines.removeIf { it.first == line }
      layeredPane.repaint()
    }
  }

  private fun scheduleSelectionUpdate() {
    if (selectionUpdateScheduled.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater {
        try {
          if (!editorImpl.isDisposed) {
            updateSelectionByCarets()
          }
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

  override fun inlayClicked(clickedCell: NotebookCellLines.Interval, ctrlPressed: Boolean, shiftPressed: Boolean, mouseButton: Int) {
    editorImpl.setMode(NotebookEditorMode.COMMAND)
    updateSelectionAfterClick(clickedCell, ctrlPressed, shiftPressed, mouseButton)
  }

  @Suppress("ConvertArgumentToSet")
  private fun updateSelectionAfterClick(clickedCell: NotebookCellLines.Interval, ctrlPressed: Boolean, shiftPressed: Boolean, mouseButton: Int) {
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
      mouseButton == MouseEvent.BUTTON1 && !model.isSelectedCell(clickedCell) -> model.selectSingleCell(clickedCell)
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
