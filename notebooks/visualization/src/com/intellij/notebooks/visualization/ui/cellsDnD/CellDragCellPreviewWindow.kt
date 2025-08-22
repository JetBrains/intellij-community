// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cellsDnD

import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JWindow

class CellDragCellPreviewWindow(@Nls private val text: String, private val editor: EditorImpl) : JWindow() {
  init {
    val label = JLabel(text).apply {
      val fontSize = editor.colorsScheme.editorFontSize
      font = editor.colorsScheme.getFont(EditorFontType.PLAIN).deriveFont(fontSize)
      border = JBUI.Borders.empty(PADDING)
      foreground = editor.colorsScheme.defaultForeground
    }

    contentPane = JPanel().apply {
      layout = BorderLayout()
      background = editor.colorsScheme.defaultBackground
      border = BorderFactory.createLineBorder(JBColor.LIGHT_GRAY, BORDER_WIDTH, true)

      if(WindowManager.getInstance().isAlphaModeSupported) {
        opacity = OPACITY
      }

      add(label, BorderLayout.CENTER)
    }

    isAlwaysOnTop = true
    pack()
  }

  fun followCursor(point: Point) {
    location = Point(point.x + CURSOR_OFFSET, point.y + CURSOR_OFFSET)
  }

  companion object {
    private val PADDING = JBUIScale.scale(8)
    private val BORDER_WIDTH = JBUIScale.scale(1)
    private const val OPACITY = 0.8f
    private val CURSOR_OFFSET = JBUIScale.scale(5)
  }
}