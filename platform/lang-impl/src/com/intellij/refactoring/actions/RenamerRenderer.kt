// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions

import com.intellij.refactoring.rename.Renamer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

internal class RenamerRenderer : ListCellRenderer<Renamer> {

  private val myComponent = JBLabel()

  override fun getListCellRendererComponent(list: JList<out Renamer>,
                                            value: Renamer,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    myComponent.text = value.presentableText
    myComponent.background = UIUtil.getListBackground(isSelected, cellHasFocus)
    myComponent.foreground = UIUtil.getListForeground(isSelected, cellHasFocus)
    myComponent.border = JBEmptyBorder(UIUtil.getListCellPadding())
    return myComponent
  }
}
