// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.psi.PsiElement
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.util.concurrent.Future
import javax.swing.*

internal class PsiElementBackgroundListCellRenderer(
  private val renderer: PsiElementListCellRenderer<*>
) : ListCellRenderer<PsiElement> {

  private val myLoadingComponentRenderer: ListCellRenderer<Any> = object : ColoredListCellRenderer<Any>() {
    override fun customizeCellRenderer(list: JList<out Any>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
      background = UIUtil.getListBackground(selected, hasFocus)
      icon = AnimatedIcon.Default.INSTANCE
      append(IdeBundle.message("progress.text.loading"))
    }
  }

  private val myComponent = PsiElementListCellRendererComponent()

  override fun getListCellRendererComponent(list: JList<out PsiElement>,
                                            value: PsiElement,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val future: Future<RendererComponents> = getComputer(list, renderer).computeComponentsAsync(value)
    if (!future.isDone) {
      return myLoadingComponentRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    }

    myComponent.removeAll()
    val (left, right) = future.get()

    val selectedFg = if (isSelected) UIUtil.getListSelectionForeground(cellHasFocus) else null
    val bg = if (isSelected) UIUtil.getListSelectionBackground(cellHasFocus) else left.background

    myComponent.background = bg

    if (right != null) {
      val spacer = JPanel()
      spacer.background = bg
      spacer.border = BorderFactory.createEmptyBorder(0, 2, 0, 2)
      myComponent.add(spacer, BorderLayout.CENTER)

      val rightRenderer: ListCellRenderer<Any> = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>,
                                                  value: Any?,
                                                  index: Int,
                                                  isSelected: Boolean,
                                                  cellHasFocus: Boolean): Component {
          val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
          foreground = selectedFg ?: right.foreground
          background = bg
          icon = right.icon
          text = right.text
          border = right.border
          horizontalTextPosition = right.horizontalTextPosition
          horizontalAlignment = right.horizontalAlignment
          return component
        }
      }
      val rightRendererComponent = rightRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      myComponent.add(rightRendererComponent, BorderLayout.EAST)
    }

    val leftRenderer: ListCellRenderer<PsiElement> = object : ColoredListCellRenderer<PsiElement>() {

      val itemMatchers: ItemMatchers = renderer.getItemMatchers(list, value)

      override fun customizeCellRenderer(list: JList<out PsiElement>,
                                         value: PsiElement,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) {
        background = bg
        icon = left.icon
        border = left.border

        val fragmentIterator = left.iterator()
        while (fragmentIterator.hasNext()) {
          val text = fragmentIterator.next()
          val textAttributes = fragmentIterator.textAttributes
          val effectiveTextAttributes = selectedFg?.let {
            SimpleTextAttributes.merge(textAttributes, SimpleTextAttributes(0, selectedFg))
          } ?: textAttributes
          SpeedSearchUtil.appendColoredFragmentForMatcher(
            text, this, effectiveTextAttributes, itemMatchers.nameMatcher,
            effectiveTextAttributes.bgColor, selected
          )
        }
      }
    }

    val leftRendererComponent = leftRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    myComponent.add(leftRendererComponent, BorderLayout.WEST)

    return myComponent
  }
}
