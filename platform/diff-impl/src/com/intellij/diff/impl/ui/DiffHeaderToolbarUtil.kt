// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl.ui

import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.plus
import com.intellij.util.ui.JBUI.Borders
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Just a utility because calling UI DSL from Java is too inconvenient
 */
@ApiStatus.Internal
object DiffHeaderToolbarUtil {
  @JvmStatic
  fun createLayoutPanel(
    leftComponent: JComponent,
    vararg rightComponents: JComponent,
  ): JPanel {
    return panel {
      val spacing = object : SpacingConfiguration by EmptySpacingConfiguration() {
        override val horizontalDefaultGap: Int get() = 10
      }
      customizeSpacingConfiguration(spacing) {
        row {
          cell(leftComponent).align(AlignX.LEFT + AlignY.CENTER).resizableColumn()
          rightComponents.forEach {
            cell(it).align(AlignX.RIGHT + AlignY.CENTER)
          }
        }.layout(RowLayout.INDEPENDENT).resizableRow()
      }
    }.andTransparent().withBorder(Borders.empty(0, 1))
  }
}