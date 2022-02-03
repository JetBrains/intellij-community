/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.border.Border
import javax.swing.event.MouseInputAdapter
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumnModel
import javax.swing.table.TableModel

/**
 *  Table by latest guidelines
 *  https://jetbrains.github.io/ui/controls/table/
 */
open class MaterialTable : JBTable {

  constructor() : super()
  constructor(model: TableModel, columnModel: TableColumnModel) : super(model, columnModel)

  /** TODO need to discuss with UI/UX dep. */
  class SimpleHeaderRenderer : JLabel(), TableCellRenderer {

    companion object {
      val cellBorder: Border? = IdeBorderFactory.createBorder(SideBorder.RIGHT)
    }

    init {
      font = JBUI.Fonts.label().deriveFont(Font.BOLD)
      isOpaque = false // In the other case label will not fill the background.
      background = HEADER_BACKGROUND
      verticalAlignment = SwingConstants.CENTER
    }

    override fun getPreferredSize(): Dimension {
      val d = super.getPreferredSize()
      d.height = (font.size*2.1).toInt()
      return d
    }

    override fun getTableCellRendererComponent(table: JTable,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): Component {

      border = if (table.columnCount == column + 1) null else cellBorder
      text = " ${value.toString()} "
      return this
    }
  }

  private var rollOverRowIndex = -1

  init {
    autoResizeMode = JTable.AUTO_RESIZE_OFF
    //table.fillsViewportHeight = true
    background = EditorColorsManager.getInstance().globalScheme.defaultBackground
    setShowColumns(true)
    autoCreateRowSorter = true
    rowHeight = (font.size * 1.8).toInt()

    //table.background = Color.white
    setShowGrid(false)
    tableHeader.defaultRenderer = SimpleHeaderRenderer()
    tableHeader.isOpaque = false
    tableHeader.background = HEADER_BACKGROUND
    tableHeader.resizingAllowed = true
    tableHeader.reorderingAllowed = false // Temporary disabled because of visual artifacts. Should be enabled when we will finish with new table component.

    tableHeader.border = IdeBorderFactory.createBorder(SideBorder.BOTTOM or SideBorder.TOP)

    val mouseListener = object : MouseInputAdapter() {

      override fun mouseExited(e: MouseEvent) {
        rollOverRowIndex = -1
        repaint()
      }

      override fun mouseMoved(e: MouseEvent) {
        val row = rowAtPoint(e.point)
        if (row != rollOverRowIndex) {
          rollOverRowIndex = row
          repaint()
        }
      }
    }

    addMouseMotionListener(mouseListener)
    addMouseListener(mouseListener)
  }

  fun isHighlightRowSelected(row: Int) = isRowSelected(row) || row == rollOverRowIndex

  fun isRollOverRowIndex(row: Int): Boolean = row == rollOverRowIndex

  /** We are preparing renderer background for mouse hovered row. */
  override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
    val c = super.prepareRenderer(renderer, row, column)
    if (isHighlightRowSelected(row)) {
      c.foreground = getSelectionForeground()
      c.background = getSelectionBackground()
    }
    else {
      c.foreground = foreground
      c.background = background
    }
    return c
  }
}

val HEADER_BACKGROUND: Color = JBColor.PanelBackground