// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.ui.UISettings
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.Gray
import com.intellij.ui.RelativeFont
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.*
import java.awt.Color
import java.awt.Font
import java.awt.Insets

internal fun navBarItemBackground(selected: Boolean, focused: Boolean): Color {
  return if (selected && focused) {
    UIUtil.getListSelectionBackground(true)
  }
  else {
    UIUtil.getListBackground()
  }
}

internal fun  navBarItemForeground(selected: Boolean, focused: Boolean, inactive: Boolean): Color? {
  return if (StartupUiUtil.isUnderDarcula()) {
    if (inactive) {
      Gray._140
    }
    else {
      defaultNavBarItemForeground(selected, focused, false)
    }
  }
  else {
    defaultNavBarItemForeground(selected, focused, inactive)
  }
}

internal fun defaultNavBarItemForeground(selected: Boolean, focused: Boolean, inactive: Boolean): Color? {
  return if (selected && focused) {
    NamedColorUtil.getListSelectionForeground(true)
  }
  else if (inactive) {
    NamedColorUtil.getInactiveTextColor()
  }
  else {
    null
  }
}

internal fun navBarItemFont(): Font? {
  if (!ExperimentalUI.isNewUI() && UISettings.getInstance().useSmallLabelsOnTabs) {
    return RelativeFont.SMALL.derive(StartupUiUtil.getLabelFont())
  }
  return StartupUiUtil.getLabelFont()
}

internal fun navBarItemInsets(): Insets {
  return if (ExperimentalUI.isNewUI()) {
    JBUI.insets("StatusBar.Breadcrumbs.itemBackgroundInsets", JBUI.insets(1))
  }
  else {
    JBInsets.emptyInsets()
  }
}

internal fun navBarPopupItemInsets(): Insets {
  return JBInsets.create(1, 2)
}

internal fun navBarItemPadding(floating: Boolean): Insets {
  if (!ExperimentalUI.isNewUI()) {
    return JBUI.insets(3)
  }
  if (floating) {
    return JBUI.insets("StatusBar.Breadcrumbs.floatingItemInsets", JBUI.insets(1))
  }
  else {
    return JBUI.insets("StatusBar.Breadcrumbs.itemInsets", JBUI.insets(2, 0))
  }
}

internal fun navBarPopupOffset(firstItem: Boolean): Int {
  return if (firstItem) 0 else JBUIScale.scale(5)
}
