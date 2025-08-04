package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isOrdinaryNotebookEditor
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.ui.cellsDnD.EditorCellDragAssistant
import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.use
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.RectanglePainter2D
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.MouseAdapter
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
        val panel = createFoldingBar()
        editor.gutterComponentEx.add(panel)
        this.panel = panel
        updateBounds()
      }
      else {
        removePanel()
      }
    }

  var selected: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        panel?.background = getBarColor()
      }
    }

  init {
    registerListeners()
  }

  private fun registerListeners() {
    JupyterBoundsChangeHandler.get(editor).subscribe(this, ::updateBounds)
    editor.notebookAppearance.cellStripeSelectedColor.afterChange(this) {
      updateBarColor()
    }
    editor.notebookAppearance.cellStripeHoveredColor.afterChange(this) {
      updateBarColor()
    }
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

  private fun createFoldingBar() = EditorCellFoldingBarComponent().apply {
    background = getBarColor()
  }

  inner class EditorCellFoldingBarComponent : JComponent() {
    private var mouseOver = false

    init {
      addMouseListener(object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) {
          mouseOver = true
          repaint()
        }

        override fun mouseExited(e: MouseEvent) {
          mouseOver = false
          repaint()
        }

        override fun mousePressed(e: MouseEvent) {
          if (SwingUtilities.isLeftMouseButton(e)) {
            draggableAdapter?.initDrag(e)
          }
        }

        override fun mouseReleased(e: MouseEvent) {
          if (SwingUtilities.isLeftMouseButton(e)) {
            draggableAdapter?.finishDrag(e)
          }
        }

        override fun mouseClicked(e: MouseEvent) {
          when (draggableAdapter) {
            null -> toggleListener.invoke()
            else -> if (!draggableAdapter.isDragging) toggleListener.invoke()
          }
        }
      })

      addMouseMotionListener(object : MouseAdapter() {
        override fun mouseDragged(e: MouseEvent) {
          draggableAdapter?.updateDragOperation(e)
        }
      })

      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun paint(g: Graphics) {
      val rect = rect()
      val arc = if (ExperimentalUI.isNewUI()) {
        rect.width.toDouble()
      }
      else {
        null
      }
      g.create().use { g2 ->
        g2 as Graphics2D
        g2.color = background
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

  private fun updateBarColor() = panel?.background = getBarColor()

  private fun getBarColor(): Color = if (selected) {
    editor.notebookAppearance.cellStripeSelectedColor.get()
  }
  else {
    editor.notebookAppearance.cellStripeHoveredColor.get()
  }
}