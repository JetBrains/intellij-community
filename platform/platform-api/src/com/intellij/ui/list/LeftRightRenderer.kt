// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.list

import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * A renderer which combines two renderers.
 *
 * [mainRenderer] component is aligned to the left, [rightRenderer] component is aligned to the right.
 * This renderer uses background from [mainRenderer] component.
 */
abstract class LeftRightRenderer<T> : ListCellRenderer<T> {

  protected abstract val mainRenderer: ListCellRenderer<T>
  protected abstract val rightRenderer: ListCellRenderer<T>

  private val spacer = JPanel().apply {
    border = JBUI.Borders.empty(0, 2)
  }

  private val component = JPanel(BorderLayout())

  final override fun getListCellRendererComponent(list: JList<out T>,
                                                  value: T,
                                                  index: Int,
                                                  isSelected: Boolean,
                                                  cellHasFocus: Boolean): Component {
    val mainComponent = mainRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    val rightComponent = rightRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    mainComponent.background.let {
      component.background = it
      spacer.background = it
      rightComponent.background = it
    }
    component.apply {
      removeAll()
      add(mainComponent, BorderLayout.WEST)
      add(spacer, BorderLayout.CENTER)
      add(rightComponent, BorderLayout.EAST)
      accessibleContext.accessibleName = listOfNotNull(
        mainComponent.accessibleContext?.accessibleName,
        rightComponent.accessibleContext?.accessibleName
      ).joinToString(separator = " ")
    }
    return component
  }
}
