// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.ui.ActiveComponent
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JPanel

internal fun createPanel(vararg components: ActiveComponent): JPanel {
  val result = JPanel(GridLayout())
  result.border = JBUI.Borders.empty(2)
  result.isOpaque = false
  val builder = RowsGridBuilder(result).resizableRow()
  for ((index, component) in components.withIndex()) {
    builder.cell(component.component, gaps = UnscaledGaps(left = if (index == 0) 0 else 2))
  }
  return result
}