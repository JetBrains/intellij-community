// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.ui

import com.intellij.platform.navbar.NavBarItemPresentationData
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.RelativeFont
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Font

internal class NavBarPopupItemComponent(
  presentation: NavBarItemPresentationData,
  selected: Boolean,
  floating: Boolean,
) : SimpleColoredComponent() {

  init {
    val attributes = presentation.textAttributes ?: SimpleTextAttributes.REGULAR_ATTRIBUTES
    val bg = navBarItemBackground(selected, true)
    val fg: Color? = if (ExperimentalUI.isNewUI()) {
      when {
        selected -> JBUI.CurrentTheme.StatusBar.Breadcrumbs.SELECTION_FOREGROUND
        floating -> attributes.fgColor ?: JBUI.CurrentTheme.StatusBar.Breadcrumbs.FLOATING_FOREGROUND
        else -> attributes.fgColor ?: JBUI.CurrentTheme.List.foreground(false, true)
      }
    }
    else {
      navBarItemForeground(selected, true, false) ?: attributes.fgColor
    }

    isOpaque = false
    ipad = navBarPopupItemInsets()
    icon = presentation.icon
    isIconOpaque = false
    setFocusBorderAroundIcon(true)
    if (ExperimentalUI.isNewUI()) {
      iconTextGap = scale(4)
    }
    background = bg
    font = RelativeFont.NORMAL.fromResource("NavBar.fontSizeOffset", 0).derive(font)

    val style = if (ExperimentalUI.isNewUI()) SimpleTextAttributes.STYLE_PLAIN else attributes.style
    val waveColor = if (ExperimentalUI.isNewUI()) null else attributes.waveColor
    append(presentation.popupText ?: presentation.text, SimpleTextAttributes(bg, fg, waveColor, style))
  }

  override fun getFont(): Font? = navBarItemFont()

  override fun setOpaque(isOpaque: Boolean): Unit = super.setOpaque(false)

  override fun getMinimumSize(): Dimension = preferredSize
}
