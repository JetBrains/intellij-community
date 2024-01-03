// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

internal class SENewUIHeaderView(tabs: List<SearchEverywhereHeader.SETab>, private val shortcutSupplier: Function<in String, String?>,
                                 toolbar: JComponent) {

  lateinit var tabbedPane: JBTabbedPane

  @JvmField
  val panel: DialogPanel

  init {
    panel = panel {
      row {
        tabbedPane = tabbedPaneHeader()
          .customize(UnscaledGaps.EMPTY)
          .applyToComponent {
            font = JBFont.regular()
            background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND
            isFocusable = false
          }
          .component
        toolbar.putClientProperty(ActionToolbarImpl.USE_BASELINE_KEY, true)
        cell(toolbar)
          .resizableColumn()
          .align(AlignX.RIGHT)
      }
    }

    val headerInsets = JBUI.CurrentTheme.ComplexPopup.headerInsets()
    @Suppress("UseDPIAwareBorders")
    panel.border = JBUI.Borders.compound(
      JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
      EmptyBorder(0, headerInsets.left, 0, headerInsets.right))

    for (tab in tabs) {
      @NlsSafe val shortcut = shortcutSupplier.apply(tab.id)
      tabbedPane.addTab(tab.name, null, JPanel(), shortcut)
    }
  }

  fun addTab(tab: SearchEverywhereHeader.SETab) {
    @NlsSafe val shortcut = shortcutSupplier.apply(tab.id)
    tabbedPane.addTab(tab.name, null, JPanel(), shortcut)
  }
}
