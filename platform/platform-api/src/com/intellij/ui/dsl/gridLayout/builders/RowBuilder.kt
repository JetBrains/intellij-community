// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout.builders

import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * DON'T USE IT, for internal Jetbrains UI team only. Can be removed in any time
 *
 * This class is a temporary solution for building one row layout via [GridLayout] from JAVA classes.
 * Useful when single BorderLayout is not enough or vertical alignment is needed, for example
 */
@ApiStatus.Internal
class RowBuilder(private val panel: JPanel) {

  private val builder: RowsGridBuilder

  private var gap = 0

  private var verticalAlign = VerticalAlign.CENTER

  init {
    panel.layout = GridLayout()
    builder = RowsGridBuilder(panel)
    builder.resizableRow()
  }

  fun gap(gap: Int): RowBuilder {
    this.gap = gap
    return this
  }

  fun verticalAlign(verticalAlign: VerticalAlign): RowBuilder {
    this.verticalAlign = verticalAlign
    return this
  }

  fun add(vararg components: JComponent): RowBuilder {
    for (component in components) {
      add(component, false)
    }
    return this
  }

  fun addResizable(component: JComponent): RowBuilder {
    add(component, true)
    return this
  }

  private fun add(component: JComponent, resizable: Boolean) {
    val gaps = if (panel.componentCount == 0 || gap == 0) Gaps.EMPTY else Gaps(left = gap)
    builder.cell(component, gaps = gaps, verticalAlign = verticalAlign, resizableColumn = resizable,
                 horizontalAlign = if (resizable) HorizontalAlign.FILL else HorizontalAlign.LEFT)
  }
}
