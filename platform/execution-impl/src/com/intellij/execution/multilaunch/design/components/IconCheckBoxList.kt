package com.intellij.execution.multilaunch.design.components

import com.intellij.ui.CheckBoxList
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.*

abstract class IconCheckBoxList<T>(model: DefaultListModel<T>) : CheckBoxList<T>(model) {
  constructor() : this(DefaultListModel())
  override fun adjustRendering(rootComponent: JComponent?, checkBox: JCheckBox?, index: Int, selected: Boolean, hasFocus: Boolean): JComponent {
    checkBox ?: return super.adjustRendering(rootComponent, checkBox, index, selected, hasFocus)
    checkBox.text = ""
    val wrapper = JBUI.Panels.simplePanel().apply {
      background = getBackground(false)
      val item = getItemAt(index)
      val icon = getIcon(item)
      val text = getText(item)
      val label = JLabel(text, icon ?: EmptyIcon.ICON_16, SwingConstants.LEFT).apply {
        isOpaque = true
        background = getBackground(selected)
      }
      add(checkBox, BorderLayout.WEST)
      add(label, BorderLayout.CENTER)
    }

    return wrapper
  }

  abstract fun getIcon(item: T?): Icon?
  @Nls
  abstract fun getText(item: T?): String?
}