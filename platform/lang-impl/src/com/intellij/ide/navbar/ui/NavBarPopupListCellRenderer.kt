// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navbar.vm.NavBarPopupItem
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

internal class NavBarPopupListCellRenderer(
  private val floating: Boolean,
) : ListCellRenderer<NavBarPopupItem> {

  override fun getListCellRendererComponent(
    list: JList<out NavBarPopupItem>,
    value: NavBarPopupItem,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    if (!ExperimentalUI.isNewUI()) {
      return NavBarPopupItemComponent(value.presentation, isSelected, floating)
    }
    val selectable: SelectablePanel = SelectablePanel.wrap(NavBarPopupItemComponent(value.presentation, isSelected, floating))
    selectable.selectionArc = JBUI.CurrentTheme.Popup.Selection.ARC.get()
    selectable.background = null
    selectable.selectionColor = if (isSelected) UIUtil.getListSelectionBackground(cellHasFocus) else null
    return selectable
  }
}
