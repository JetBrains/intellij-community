package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.RectanglePainter2D
import org.jetbrains.plugins.notebooks.ui.visualization.notebookAppearance
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

internal class EditorCellFoldingBar(
  private val editor: EditorEx,
  private val toggleListener: () -> Unit
) {

  val panel = object : JComponent() {

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
      val graphics2D = g as Graphics2D
      graphics2D.color = color
      RectanglePainter2D.FILL.paint(graphics2D, rect, arc, LinePainter2D.StrokeType.INSIDE, 1.0, RenderingHints.VALUE_ANTIALIAS_DEFAULT)
    }

    private fun rect(): Rectangle {
      val size = size
      val width = size.width
      val height = size.height
      return if (mouseOver) {
        Rectangle(0, 0, width, height)
      }
      else {
        Rectangle(1, 1, width - 2, height - 2)
      }
    }
  }

  var visible: Boolean
    get() = panel.isVisible
    set(value) {
      panel.isVisible = value
    }

  var selected: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        panel.repaint()
      }
    }

  init {
    editor.gutterComponentEx.add(panel)
  }

  fun dispose() {
    editor.gutterComponentEx.remove(panel)
  }

  fun setLocation(y: Int, height: Int) {
    panel.location = Point(editor.gutterComponentEx.extraLineMarkerFreePaintersAreaOffset + 1, y)
    panel.size = Dimension(6, height)
  }
}