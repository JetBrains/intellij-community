// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup.util

import com.intellij.ui.ExperimentalUI
import com.intellij.ui.popup.list.SelectablePanel.Companion.wrap
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Use [com.intellij.ui.dsl.listCellRenderer.BuilderKt.textListCellRenderer/listCellRenderer] when possible
 */
class RoundedCellRenderer<T> @JvmOverloads constructor(private val renderer: ListCellRenderer<T>,
                                                       private val fixedHeight: Boolean = true) : ListCellRenderer<T> {
  override fun getListCellRendererComponent(list: JList<out T>, value: T, index: Int, isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    if (!ExperimentalUI.isNewUI()) {
      return renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    }

    val unselectedComponent = renderer.getListCellRendererComponent(list, value, index, false, cellHasFocus)
    val rowBackground = getBackground(unselectedComponent, list)
    val component = renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    val result = wrap(component, rowBackground)
    if (fixedHeight)  {
      PopupUtil.configListRendererFixedHeight(result)
    } else {
      PopupUtil.configListRendererFlexibleHeight(result)
    }
    if (isSelected) {
      result.selectionColor = getBackground(component, list)
    }
    (component as JComponent).isOpaque = false
    return result
  }

  private fun getBackground(component: Component, list: JList<*>): Color? {
    val background = component.background
    return if (background === UIUtil.getListBackground()) list.background else background
  }
}