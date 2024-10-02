// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.remote

import com.intellij.ide.actions.SearchEverywherePsiRenderer.SELayout
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.render.RendererPanelsUtils.Companion.iconTextGap
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.text.MatcherHolder
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Insets
import javax.swing.*

@ApiStatus.Internal
class PSIPresentation(val targetPresentation: TargetPresentation) : RemoteSearchEverywherePresentation {
  override fun getPresentationRenderer(): ListCellRenderer<out RemoteSearchEverywherePresentation> = PSIPresentationRenderer()
}


private class PSIPresentationRenderer : JPanel(SELayout()), ListCellRenderer<PSIPresentation> {
  override fun getListCellRendererComponent(list: JList<out PSIPresentation>,
                                            value: PSIPresentation,
                                            index: Int,
                                            selected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val presentation = value.targetPresentation
    removeAll()
    var rightComponent: JLabel? = null
    if (StringUtil.isNotEmpty(presentation.locationText)) {
      rightComponent = JLabel(presentation.locationText, presentation.locationIcon, SwingConstants.RIGHT)
      rightComponent.horizontalTextPosition = SwingConstants.LEFT
      rightComponent.iconTextGap = iconTextGap
      rightComponent.foreground = if (selected) NamedColorUtil.getListSelectionForeground(true) else NamedColorUtil.getInactiveTextColor()
      add(rightComponent, BorderLayout.EAST)
    }
    val leftComponent = SimpleColoredComponent()
    leftComponent.ipad = Insets(0, 0, 0, leftComponent.ipad.right) // Border of top panel is used for around insets of renderer
    leftComponent.icon = presentation.icon
    leftComponent.iconTextGap = iconTextGap
    leftComponent.font = list.font
    val nameAttributes = if (presentation.presentableTextAttributes != null) SimpleTextAttributes.fromTextAttributes(
      presentation.presentableTextAttributes)
    else SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, null)
    val nameMatcher = MatcherHolder.getAssociatedMatcher(list)
    val bgColor = if (selected) UIUtil.getListSelectionBackground(true) else presentation.backgroundColor!!
    background = bgColor
    SpeedSearchUtil.appendColoredFragmentForMatcher(presentation.presentableText, leftComponent, nameAttributes, nameMatcher, bgColor,
                                                    selected)
    if (presentation.containerText != null) {
      val listInsets = list.insets
      val rendererInsets = leftComponent.insets
      val fm = list.getFontMetrics(list.font)
      var containerMaxWidth = (list.width - listInsets.left - listInsets.right
                               - rendererInsets.left - rendererInsets.right
                               - leftComponent.preferredSize.width)
      if (rightComponent != null) containerMaxWidth -= rightComponent.preferredSize.width
      val containerText: @NlsSafe String? = PaintUtil.cutContainerText(presentation.containerText, containerMaxWidth, fm)
      val containerAttributes = if (presentation.containerTextAttributes != null) SimpleTextAttributes.fromTextAttributes(
        presentation.containerTextAttributes)
      else SimpleTextAttributes.GRAYED_ATTRIBUTES
      SpeedSearchUtil.appendColoredFragmentForMatcher(" $containerText", leftComponent, containerAttributes, null, bgColor, selected)
    }
    add(leftComponent, BorderLayout.WEST)
    accessibleContext = leftComponent.accessibleContext
    return this
  }
}


