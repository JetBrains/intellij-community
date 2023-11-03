// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util.popup

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.openapi.ui.popup.util.RoundedCellRenderer
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

class SimplePopupItemRenderer<T> private constructor(private val presenter: (T) -> PopupItemPresentation) : ColoredListCellRenderer<T>() {
  init {
    iconTextGap = JBUIScale.scale(4)
  }

  override fun customizeCellRenderer(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
    val presentation = presenter(value)
    icon = presentation.icon
    append(presentation.shortText)
    val fullText = presentation.fullText
    if (fullText != null) {
      append(" ")
      append(fullText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    // ColoredListCellRenderer sets null for a background in case of !selected, so it can't work with SelectablePanel
    if (!selected) background = list.background
  }

  companion object {
    fun <T> create(presenter: (T) -> PopupItemPresentation): ListCellRenderer<T> {
      val simplePopupItemRenderer = SimplePopupItemRenderer(presenter)
      if (!ExperimentalUI.isNewUI())
        return simplePopupItemRenderer

      simplePopupItemRenderer.ipad.left = 0
      simplePopupItemRenderer.ipad.right = 0
      return RoundedCellRenderer(simplePopupItemRenderer, false)
    }
  }
}

class SimpleSelectablePopupItemRenderer<T> private constructor(private val reviewerPresenter: (T) -> SelectablePopupItemPresentation) : ListCellRenderer<T> {
  private val checkBox: JBCheckBox = JBCheckBox().apply {
    isOpaque = false
  }
  private val label: SimpleColoredComponent = SimpleColoredComponent().apply {
    iconTextGap = JBUIScale.scale(4)
  }
  private val panel = BorderLayoutPanel(6, 5).apply {
    addToLeft(checkBox)
    addToCenter(label)
    border = JBUI.Borders.empty(TOP_BOTTOM_GAP, LEFT_RIGHT_GAP)
  }

  override fun getListCellRendererComponent(list: JList<out T>,
                                            value: T,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val presentation = reviewerPresenter(value)

    checkBox.apply {
      this.isSelected = presentation.isSelected
      isFocusPainted = cellHasFocus
      isFocusable = cellHasFocus
    }

    label.apply {
      clear()
      append(presentation.shortText)
      icon = presentation.icon
      foreground = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
    }

    UIUtil.setBackgroundRecursively(panel, ListUiUtil.WithTallRow.background(list, isSelected, true))

    return panel
  }

  companion object {
    fun <T> create(presenter: (T) -> SelectablePopupItemPresentation): ListCellRenderer<T> {
      val simplePopupItemRenderer = SimpleSelectablePopupItemRenderer(presenter)
      if (!ExperimentalUI.isNewUI())
        return simplePopupItemRenderer

      return RoundedCellRenderer(simplePopupItemRenderer, false)
    }

    private const val TOP_BOTTOM_GAP = 1
    private val LEFT_RIGHT_GAP: Int
      get() = CollaborationToolsUIUtil.getSize(oldUI = 5, newUI = 0) // in case of the newUI gap handled by SelectablePanel
  }
}