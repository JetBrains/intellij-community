// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.gridLayoutTestAction

import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel

internal class MinimumSizePanel {

  val panel: JPanel = createTabPanel("Minimum and preferred sizes", createContent())

  private fun createContent(): JPanel {
    val layout = GridLayout()
    val result = JPanel(layout)
    val builder = RowsGridBuilder(result)

    layout.respectMinimumSize = true

    for (y in 0..2) {
      val resizableRow = y == 1
      builder.row(resizable = resizableRow)

      for (x in 0..2) {
        val resizableColumn = x >= 1
        val label = JLabel()

        if (!resizableColumn && !resizableRow) {
          label.minimumSize = Dimension(0, 0) // Shouldn't be taken into account
        }

        val title = mutableListOf<String>()
        if (x == 1 && y == 1) {
          label.preferredSize = Dimension(400, 100)
          title.add("preferredSize = ${label.preferredSize}")
        }

        builder.cell(label, resizableColumn = resizableColumn,
                     horizontalAlign = HorizontalAlign.FILL,
                     verticalAlign = VerticalAlign.FILL)
        val constraints = layout.getConstraints(label)
        label.text = "<html>" + title.joinToString("<br>", postfix = "<br>") + constraintsToHtmlString(constraints!!)
      }
    }

    return result
  }
}
