package com.intellij.notebooks.visualization.outputs.impl

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.openapi.editor.Editor
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import javax.swing.border.Border

/** Special border which could draw 'resize' line. */
class CollapsingComponentBorder(private val editor: Editor) : Border {

  /** True if the border is currently resized. */
  var resized = false

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {

    if (!resized) return

    val g2 = g.create()
    try {
      g2.color = editor.notebookAppearance.getCodeCellBackground(editor.colorsScheme)
      val insets = getBorderInsets(c)
      assert(insets.top + insets.left + insets.right == 0)
      val yDraw = y + height - insets.bottom / 2
      g2.fillRect(x, yDraw, width, JBUI.scale(1))
    }
    finally {
      g2.dispose()
    }
  }

  override fun getBorderInsets(c: Component): Insets = borderInsets

  override fun isBorderOpaque(): Boolean = false

  companion object {
    private val borderInsets = JBUI.insetsBottom(JBUI.scale(4))
  }
}