package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.visualization.NotebookUtil.isOrdinaryNotebookEditor
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.ui.cellsDnD.EditorCellDragAssistant
import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeNotifier
import com.intellij.notebooks.visualization.useG2D
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.RectanglePainter2D
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

@ApiStatus.Internal
class EditorCellFoldingBar(
  private val editor: EditorImpl,
  private val draggableAdapter: EditorCellDragAssistant?,
  private val yAndHeightSupplier: () -> Pair<Int, Int>,
  private val toggleListener: () -> Unit,
) : Disposable {
  // Why not use the default approach with RangeHighlighters?
  // Because it is not possible to create RangeHighlighter for every single inlay in range,
  // RangeHighlighter created for text range and covered all inlays in range.
  private var panel: JComponent? = null

  var visible: Boolean
    get() = panel?.isVisible == true
    set(value) {
      if (visible == value) return
      if (!editor.isOrdinaryNotebookEditor()) return

      if (value) {
        val panel = EditorCellFoldingBarComponent()
        editor.gutterComponentEx.add(panel)
        this.panel = panel
        updateBounds()
      }
      else {
        if (draggableAdapter?.isDragging == true) return
        removePanel()
      }
    }

  var selected: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        panel?.repaint()
      }
    }

  init {
    registerListeners()
  }

  private fun registerListeners() {
    JupyterBoundsChangeNotifier.get(editor).subscribe(this, ::updateBounds)
  }

  override fun dispose() {
    removePanel()
  }

  private fun removePanel() {
    panel?.let {
      editor.gutterComponentEx.apply {
        remove(it)
        repaint()
      }
      panel = null
    }
  }

  fun updateBounds() {
    runInEdt {
      val panel = panel ?: return@runInEdt
      val (y, height) = yAndHeightSupplier.invoke()
      panel.setBounds(editor.gutterComponentEx.annotationsAreaOffset - 2, y, 6, height)
    }
  }

  inner class EditorCellFoldingBarComponent : JComponent() {
    private var mouseOver = false

    init {
      enableEvents(AWTEvent.MOUSE_EVENT_MASK)

      if (draggableAdapter != null) {
        enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK)
      }

      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun processMouseEvent(e: MouseEvent) {
      when (e.id) {
        MouseEvent.MOUSE_ENTERED -> {
          mouseOver = true
          repaint()
        }
        MouseEvent.MOUSE_EXITED -> {
          mouseOver = false
          repaint()
        }
        MouseEvent.MOUSE_CLICKED -> {
          if (draggableAdapter?.isDragging == true) return
          toggleListener.invoke()
        }
        MouseEvent.MOUSE_PRESSED -> {
          if (SwingUtilities.isLeftMouseButton(e)) {
            draggableAdapter?.initDrag(e)
          }
        }
        MouseEvent.MOUSE_RELEASED -> {
          if (SwingUtilities.isLeftMouseButton(e)) {
            draggableAdapter?.finishDrag(e)
          }
        }
      }
    }

    override fun processMouseMotionEvent(e: MouseEvent) {
      when (e.id) {
        MouseEvent.MOUSE_DRAGGED -> {
          draggableAdapter?.updateDragOperation(e)
        }
      }
    }

    private fun getBackgroundColor(): Color {
      return if (selected) {
        editor.notebookAppearance.cellStripeSelectedColor()
      }
      else {
        editor.notebookAppearance.cellStripeHoveredColor()
      }
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)

      val rect = rect()
      val arc = if (ExperimentalUI.isNewUI()) {
        rect.width.toDouble()
      }
      else {
        null
      }
      g.useG2D { g2 ->
        g2.color = getBackgroundColor()
        RectanglePainter2D.FILL.paint(g2, rect, arc, LinePainter2D.StrokeType.INSIDE, 1.0, RenderingHints.VALUE_ANTIALIAS_ON)
      }
    }

    // Returns rect size for drawing rounded corners rect of folding area
    private fun rect(): Rectangle {
      val size = size
      val width = size.width
      val height = size.height
      return if (mouseOver) {
        Rectangle(0, 0, width, height)
      }
      else {
        //TODO Will not properly work with different UI scales.
        Rectangle(1, 1, width - 2, height - 2)
      }
    }
  }
}