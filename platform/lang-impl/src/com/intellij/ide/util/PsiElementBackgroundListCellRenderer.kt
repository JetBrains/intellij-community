// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.navigation.LocationPresentation
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Deferred
import java.awt.Component
import java.awt.Dimension
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

  var usedInPopup: Boolean = false
    set(value) {
      if (ExperimentalUI.isNewUI()) {
        PopupUtil.configListRendererFixedHeight(myComponent)
      }
      field = value
    }

  override fun getListCellRendererComponent(list: JList<out PsiElement>,
                                            value: PsiElement,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val future: Deferred<TargetPresentation> = getComputer(list).computePresentationAsync(renderer, value)
    if (!future.isCompleted) {
      return myLoadingComponentRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    }

    myComponent.removeAll()
    @Suppress("EXPERIMENTAL_API_USAGE")
    val presentation = future.getCompleted()

    val defaultBg = if (usedInPopup && ExperimentalUI.isNewUI()) JBUI.CurrentTheme.Popup.BACKGROUND else UIUtil.getListBackground()
    myComponent.background = presentation.backgroundColor ?: defaultBg
    val bg = if (isSelected) {
      UIUtil.getListSelectionBackground(cellHasFocus)
    }
    else {
      myComponent.background
    }

    myComponent.selectionColor = bg

    val spacer = JPanel().apply {
      isOpaque = false
      border = BorderFactory.createEmptyBorder(0, 2, 0, 2)
    }

    val rightComponent = presentation.locationText?.let {
      val rightRenderer: ListCellRenderer<Any> = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>,
                                                  value: Any?,
                                                  index: Int,
                                                  isSelected: Boolean,
                                                  cellHasFocus: Boolean): Component {
          val component= ReadAction.compute<Component, Error> { super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) }
          foreground = if (isSelected) NamedColorUtil.getListSelectionForeground(cellHasFocus) else NamedColorUtil.getInactiveTextColor()
          isOpaque = false
          icon = presentation.locationIcon
          text = it
          if (!usedInPopup || !ExperimentalUI.isNewUI()) {
            border = BorderFactory.createEmptyBorder(0, 0, 0, UIUtil.getListCellHPadding())
          }
          horizontalTextPosition = LEFT
          horizontalAlignment = RIGHT

          var minWidth = 0
          minWidth += insets.left + insets.right
          minWidth += icon.iconWidth
          minWidth += iconTextGap
          minWidth += getFontMetrics(font).stringWidth("...")
          minimumSize = JBUI.size(minWidth, minimumSize.height)

          return component
        }
      }
      rightRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    }

    val leftRenderer: ListCellRenderer<PsiElement> = object : ColoredListCellRenderer<PsiElement>() {

      val itemMatchers: ItemMatchers = renderer.getItemMatchers(list, value)

      override fun customizeCellRenderer(list: JList<out PsiElement>,
                                         value: PsiElement,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) {
        isOpaque = false
        icon = presentation.icon
        if (usedInPopup && ExperimentalUI.isNewUI()) {
          ipad.left = 0
          ipad.right = 0
        }

        val nameAttributes = presentation.presentableTextAttributes?.let(SimpleTextAttributes::fromTextAttributes)
                             ?: SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getListForeground(selected, hasFocus))
        SpeedSearchUtil.appendColoredFragmentForMatcher(
          presentation.presentableText, this, nameAttributes, itemMatchers.nameMatcher, bg, selected
        )

        minimumSize = Dimension(preferredSize.width, minimumSize.height)

        val containerText = presentation.containerText
        if (containerText != null) {
          val containerTextAttributes = presentation.containerTextAttributes?.let {
            SimpleTextAttributes.merge(SimpleTextAttributes.fromTextAttributes(it), SimpleTextAttributes.GRAYED_ATTRIBUTES)
          } ?: SimpleTextAttributes.GRAYED_ATTRIBUTES
          append(LocationPresentation.DEFAULT_LOCATION_PREFIX, SimpleTextAttributes.GRAYED_ATTRIBUTES)
          SpeedSearchUtil.appendColoredFragmentForMatcher(
            containerText, this, containerTextAttributes, itemMatchers.locationMatcher, bg, selected
          )
          append(LocationPresentation.DEFAULT_LOCATION_SUFFIX, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
      }
    }

    val leftComponent = leftRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    myComponent.add(leftComponent)
    rightComponent?.let {
      myComponent.add(spacer)
      myComponent.add(it)
    }

    return myComponent
  }
}
