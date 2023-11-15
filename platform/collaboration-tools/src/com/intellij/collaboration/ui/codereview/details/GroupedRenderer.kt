// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.util.ui.JBUI.CurrentTheme.ActionsList
import com.intellij.util.ui.JBUI.CurrentTheme.Popup
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListCellRenderer

class GroupedRenderer<T>(
  private val baseRenderer: ListCellRenderer<T>,
  private val hasSeparatorAbove: (value: T, index: Int) -> Boolean = { _, _ -> false },
  private val hasSeparatorBelow: (value: T, index: Int) -> Boolean = { _, _ -> false },
  private val buildSeparator: (value: T, index: Int, position: SeparatorPosition) -> JComponent = defaultSeparatorBuilder()
) : ListCellRenderer<T> {

  private val contentWithSeparators: BorderLayoutPanel by lazy { BorderLayoutPanel() }

  override fun getListCellRendererComponent(list: JList<out T>?,
                                            value: T,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val content = baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

    val separatorAbove = hasSeparatorAbove(value, index)
    val separatorBelow = hasSeparatorBelow(value, index)

    if (!separatorAbove && !separatorBelow) return content

    return contentWithSeparators.apply {
      background = list?.background

      removeAll()
      addToCenter(content)
      if (separatorAbove) {
        addToTop(buildSeparator(value, index, SeparatorPosition.ABOVE))
      }

      if (separatorBelow) {
        addToBottom(buildSeparator(value, index, SeparatorPosition.BELOW))
      }
    }
  }

  enum class SeparatorPosition {
    ABOVE,
    BELOW
  }

  companion object {
    fun createDefaultSeparator(text: @NlsContexts.Separator String? = null, paintLine: Boolean = false): GroupHeaderSeparator {
      val labelInsets = if (ExperimentalUI.isNewUI()) Popup.separatorLabelInsets() else ActionsList.cellPadding()
      return GroupHeaderSeparator(labelInsets).apply {
        text?.let { t ->
          setHideLine(!paintLine)
          caption = t
        }
      }
    }
  }
}

private fun <T> defaultSeparatorBuilder(): (value: T, index: Int, position: GroupedRenderer.SeparatorPosition) -> GroupHeaderSeparator {
  return { _, _, _ -> GroupedRenderer.createDefaultSeparator() }
}