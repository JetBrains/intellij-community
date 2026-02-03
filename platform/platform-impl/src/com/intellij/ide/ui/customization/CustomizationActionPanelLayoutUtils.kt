// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGapsX
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal fun setupTopPanelLayout(topPanel: JPanel, toolbarPanel: JComponent, filterPanel: JComponent) {
  val filterContainerRightAlignedWithMaxWidth = object: JPanel(BorderLayout()) {
    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
      val newWidth = if (width > maximumSize.width) maximumSize.width else width
      super.setBounds(x + (width - newWidth), y, newWidth, height)
    }
  }
  filterContainerRightAlignedWithMaxWidth.add(filterPanel, BorderLayout.CENTER)
  filterContainerRightAlignedWithMaxWidth.maximumSize = filterPanel.maximumSize

  RowsGridBuilder(topPanel)
    .resizableRow()
    .cell(toolbarPanel, horizontalAlign = HorizontalAlign.LEFT)
    .cell(filterContainerRightAlignedWithMaxWidth, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
    .columnsGaps(listOf(UnscaledGapsX(right = 32)))
}