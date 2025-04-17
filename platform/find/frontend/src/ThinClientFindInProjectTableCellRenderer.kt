// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find.frontend

import com.intellij.find.impl.FindPopupItem
import com.intellij.platform.find.UsageInfoModel
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

// TODO move back and introduce usagePresentationProvider with Extension points to collect providers
//  check canProvide -> getUsagePresentation
class ThinClientFindInProjectTableCellRenderer : JPanel(), TableCellRenderer {
  private val myUsageRenderer: ColoredTableCellRenderer = object : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(
      table: JTable,
      value: Any?,
      selected: Boolean,
      hasFocus: Boolean,
      row: Int,
      column: Int
    ) {
      var thinClientUsage = value as? UsageInfoModel
      if (thinClientUsage == null) {
        thinClientUsage = (value as? FindPopupItem)?.usage as? UsageInfoModel
      }

      if (thinClientUsage != null) {
        for (textChunk in thinClientUsage.model.presentation) {
          val attributes = textChunk.attributes.toInstance()
          this.append(textChunk.text, attributes)
        }
      }
      border = null
    }
  }
  private val myFileAndLineNumber: ColoredTableCellRenderer = object : ColoredTableCellRenderer() {
    private val REPEATED_FILE_ATTRIBUTES =
      SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor(0xCCCCCC, 0x5E5E5E))
    private val ORDINAL_ATTRIBUTES =
      SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor(0x999999, 0x999999))

    override fun customizeCellRenderer(
      table: JTable,
      value: Any?,
      selected: Boolean,
      hasFocus: Boolean,
      row: Int,
      column: Int
    ) {
      if (value is UsageInfoModel) {
        val uniqueVirtualFilePath = PathUtil.getFileName(value.model.path)
        val prevFile = findPrevFile(table, row, column)
        val attributes =
          if (value.model.path == prevFile) REPEATED_FILE_ATTRIBUTES else ORDINAL_ATTRIBUTES
        append(uniqueVirtualFilePath + " " + value.model.line, attributes)
      }
      border = null
    }

    private fun findPrevFile(table: JTable, row: Int, column: Int): String? {
      if (row <= 0) return null
      val prev = table.getValueAt(row - 1, column)
      return if (prev is UsageInfoModel) prev.model.path else null
    }
  }

  override fun getTableCellRendererComponent(
    table: JTable,
    value: Any,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int
  ): Component {
    myUsageRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    myFileAndLineNumber.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    background = myUsageRenderer.background
    return this
  }

  companion object {
    private const val MARGIN = 2
  }

  init {
    layout = BorderLayout()
    add(myUsageRenderer, BorderLayout.CENTER)
    add(myFileAndLineNumber, BorderLayout.EAST)
    border = JBUI.Borders.empty(
      MARGIN,
      MARGIN,
      MARGIN,
      0
    )
  }
}