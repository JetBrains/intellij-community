// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.border.Border
import javax.swing.plaf.UIResource

@ApiStatus.Internal
internal class DarculaComboBoxRenderer : JLabel(), ListCellRenderer<Any>, ExperimentalUI.NewUIComboBoxRenderer, UIResource {

  private var selectionColor: Color? = null

  override fun getListCellRendererComponent(list: JList<out Any?>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component? {
    val collapsedCombobox = index < 0
    border = if (collapsedCombobox) null else getItemBorder()

    if (isSelected) {
      setForeground(list.selectionForeground)
      selectionColor = list.selectionBackground
    }
    else {
      setForeground(list.getForeground())
      selectionColor = null
    }

    setBackground(list.getBackground())
    setFont(list.getFont())

    if (value is Icon) {
      icon = value
      text = null
    }
    else {
      icon = null
      @Suppress("HardCodedStringLiteral")
      text = value?.toString() ?: ""
    }

    return this
  }

  override fun getPreferredSize(): Dimension? {
    val size: Dimension

    if (this.text.isNullOrEmpty()) {
      setText(" ")
      size = super.getPreferredSize()
      setText("")
    }
    else {
      size = super.getPreferredSize()
    }

    return size
  }

  override fun paintComponent(g: Graphics?) {
    if (g == null) {
      return
    }

    g.color = background
    g.fillRect(0, 0, width, height)

    selectionColor?.let {
      val leftRightInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get()
      DarculaNewUIUtil.fillRoundedRectangle(g, Rectangle(leftRightInset, 0, width - leftRightInset * 2, height), it)
    }

    super.paintComponent(g)
  }

  private fun getItemBorder(): Border {
    val leftRightInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.unscaled.toInt()
    val innerInsets = JBUI.CurrentTheme.Popup.Selection.innerInsets().let { (it as? JBInsets)?.unscaled ?: it }

    return JBUI.Borders.empty(2, innerInsets.left + leftRightInset, 2, innerInsets.right + leftRightInset)
  }
}
