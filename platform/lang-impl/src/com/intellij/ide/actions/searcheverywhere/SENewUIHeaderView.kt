// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ui.JBUI
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel

internal class SENewUIHeaderView(tabs: List<SearchEverywhereHeader.SETab>, shortcutSupplier: Function<in String?, String?>,
                                 toolbar: JComponent) {

  lateinit var tabbedPane: JBTabbedPane

  @JvmField
  val panel: DialogPanel

  init {
    panel = panel {
      row {
        tabbedPane = tabbedPaneHeader()
          .resizableColumn()
          .verticalAlign(VerticalAlign.BOTTOM)
          .customize(Gaps.EMPTY)
          .applyToComponent {
            background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND
            isFocusable = false
          }
          .component
        cell(toolbar)
          .customize(Gaps.EMPTY)
      }
    }

    val headerInsets = JBUI.CurrentTheme.ComplexPopup.headerInsets().unscaled
    panel.border = JBUI.Borders.compound(
      JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 0, 0, 1, 0),
      JBUI.Borders.empty(0, headerInsets.left, 0, headerInsets.right))

    for (tab in tabs) {
      val shortcut = shortcutSupplier.apply(tab.id)
      tabbedPane.addTab(tab.name, null, JPanel(), shortcut)
    }
  }
}
