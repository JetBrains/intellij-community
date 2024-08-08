package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.RectanglePainter2D
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.notebooks.ui.visualization.notebookAppearance
import org.jetbrains.plugins.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import org.jetbrains.plugins.notebooks.visualization.inlay.JupyterBoundsChangeListener
import org.jetbrains.plugins.notebooks.visualization.use
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

@ApiStatus.Internal
class EditorCellFoldingBar(
  private val editor: EditorImpl,
  private val yAndHeightSupplier: () -> Pair<Int, Int>,
  private val toggleListener: () -> Unit,
) {
  // Why not use the default approach with RangeHighlighters?
  // Because it is not possible to create RangeHighlighter for every single inlay, only on a chain of consequential inlays.
  private var panel: JComponent? = null

  private val boundsChangeListener = object : JupyterBoundsChangeListener {
    override fun boundsChanged() {
      updateBounds()
    }
  }

  var visible: Boolean
    get() = panel?.isVisible == true
    set(value) {
      if (visible == value) return
      if (editor.editorKind != EditorKind.MAIN_EDITOR) return

      if (value) {
        val panel = createFoldingBar()
        editor.gutterComponentEx.add(panel)
        this.panel = panel
        JupyterBoundsChangeHandler.get(editor)?.subscribe(boundsChangeListener)
        updateBounds()
      }
      else {
        dispose()
      }
    }

  var selected: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        panel?.repaint()
      }
    }

  fun dispose() {
    panel?.let {
      editor.gutterComponentEx.apply {
        remove(it)
        repaint()
      }
      JupyterBoundsChangeHandler.get(editor)?.unsubscribe(boundsChangeListener)
      panel = null
    }
  }

  fun updateBounds() {
    val panel = panel ?: return
    val yAndHeight = yAndHeightSupplier.invoke()
    panel.setBounds(editor.gutterComponentEx.extraLineMarkerFreePaintersAreaOffset + 1, yAndHeight.first, 6, yAndHeight.second)
  }

  private fun createFoldingBar() = object : JComponent() {
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

        override fun mouseClicked(e: MouseEvent) {
          toggleListener.invoke()
        }
      })
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun paint(g: Graphics) {
      val appearance = editor.notebookAppearance
      val color = if (selected) {
        appearance.getCellStripeHoverColor(editor)
      }
      else {
        appearance.getCellStripeColor(editor)
      }
      val rect = rect()
      val arc = if (ExperimentalUI.isNewUI()) {
        rect.width.toDouble()
      }
      else {
        null
      }
      g.create().use { g2 ->
        g2 as Graphics2D
        g2.color = color
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