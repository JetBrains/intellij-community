// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.navbar.NavBarItemPresentation
import com.intellij.ui.SimpleColoredComponent
import org.apache.commons.lang.StringUtils
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel


internal class NavBarItemComponent(presentation: NavBarItemPresentation,
                                   isIconNeeded: Boolean = false,
                                   isChevronNeeded: Boolean = true) : JPanel(BorderLayout()) {
  init {
    val coloredComponent = SimpleColoredComponent().apply {
      if (isIconNeeded) {
        icon = presentation.icon
      }
      append(StringUtils.abbreviate(presentation.text, 50), presentation.textAttributes)
    }

    border = BorderFactory.createEmptyBorder(0, 5, 0, 0)

    add(coloredComponent, BorderLayout.CENTER)

    if (isChevronNeeded) {
      add(JLabel(AllIcons.Ide.NavBarSeparator), BorderLayout.EAST)
    }
  }
}


internal class NavigationBarPopupItemComponent(presentation: NavBarItemPresentation) : SimpleColoredComponent() {
  init {
    isTransparentIconBackground = true
    icon = presentation.icon
    append(StringUtils.abbreviate(presentation.popupText, 50), presentation.textAttributes)
  }
}
