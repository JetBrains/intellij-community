// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.roots.ui.configuration.ComboBoxWithSeparators.EntryModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.JList

/**
 * Simple [ComboBox] extension supporting separators.
 *
 * Use `addItem(Separator("Category Name"))` to add a separator.
 * Extend [EntryModel] for non-separator entries.
 */
abstract class ComboBoxWithSeparators<T> : ComboBox<ComboBoxWithSeparators<T>.EntryModel<T>>() {

  init {
    model = MyComboBoxModel()
    renderer = MyListCellRenderer()
    isSwingPopup = false
  }

  abstract inner class EntryModel<T>(private val myItem: T?) {
    open fun getItem(): T? = myItem
    abstract fun getPresentableText(): @NlsContexts.ListItem String
    open fun getSecondaryText(): @NlsContexts.ListItem String = ""
  }

  inner class Separator(val text: @NlsContexts.Separator String) : EntryModel<T>(null) {
    override fun getPresentableText(): String = text
  }

  private inner class MyComboBoxModel<T> : DefaultComboBoxModel<ComboBoxWithSeparators<T>.EntryModel<T>>() {
    override fun setSelectedItem(anObject: Any?) {
      if (anObject !is ComboBoxWithSeparators<*>.Separator)
        super.setSelectedItem(anObject)
    }
  }

  private inner class MyListCellRenderer: ColoredListCellRenderer<ComboBoxWithSeparators<T>.EntryModel<T>>() {
    private val separatorRenderer = SeparatorRenderer()

    override fun getListCellRendererComponent(list: JList<out EntryModel<T>>?,
                                              value: EntryModel<T>?,
                                              index: Int,
                                              selected: Boolean,
                                              hasFocus: Boolean): Component {
      return when (value) {
        is Separator -> {
          // index = -1 for ComboBox size calculation, separator shouldn't be taken into account
          separatorRenderer.init(value.text, index < 0)
          separatorRenderer
        }
        else -> super.getListCellRendererComponent(list, value, index, selected, hasFocus)
      }
    }

    override fun customizeCellRenderer(list: JList<out EntryModel<T>>,
                                       value: EntryModel<T>?,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean) {
      value?.let { entry ->
        append(entry.getPresentableText())
        val secondaryText = entry.getSecondaryText()
        if (secondaryText.isNotEmpty()) {
          append(" $secondaryText", SimpleTextAttributes.GRAY_ATTRIBUTES)
        }
      }
    }

  }

}

private class SeparatorRenderer : OpaquePanel() {

  private val separator = GroupHeaderSeparator(JBUI.insets(3, 8, 1, 0))

  private var emptyPreferredHeight = false

  init {
    layout = BorderLayout()
    separator.useComboLineInsets()
    add(separator)
  }

  fun init(@NlsContexts.Separator caption: String, emptyPreferredHeight: Boolean) {
    separator.caption = caption
    this.emptyPreferredHeight = emptyPreferredHeight
  }

  override fun getPreferredSize(): Dimension {
    var result = super.getPreferredSize()
    if (emptyPreferredHeight) {
      result = Dimension(result.width, 0)
    }
    return result
  }
}
