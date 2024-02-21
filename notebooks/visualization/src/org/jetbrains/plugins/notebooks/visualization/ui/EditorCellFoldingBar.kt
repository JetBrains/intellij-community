package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.plugins.notebooks.ui.visualization.notebookAppearance
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

internal class EditorCellFoldingBar(
  private val editor: EditorEx,
  private val toggleListener: () -> Unit
) {

  val panel = JPanel().also {

    val appearance = editor.notebookAppearance

    it.background = appearance.getCellStripeColor(editor)
    it.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    it.addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        it.background = appearance.getCellStripeHoverColor(editor)
      }

      override fun mouseExited(e: MouseEvent) {
        it.background = appearance.getCellStripeColor(editor)
      }

      override fun mouseClicked(e: MouseEvent) {
        toggleListener.invoke()
      }
    })
  }

  init {
    editor.gutterComponentEx.add(panel)
  }

  fun dispose() {
    editor.gutterComponentEx.remove(panel)
  }

  fun setLocation(y: Int, height: Int) {
    panel.location = Point(editor.gutterComponentEx.extraLineMarkerFreePaintersAreaOffset, y)
    panel.size = Dimension(8, height)
  }
}