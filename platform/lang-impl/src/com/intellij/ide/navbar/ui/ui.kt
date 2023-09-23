// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.ui.UISettings
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.Gray
import com.intellij.ui.RelativeFont
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.*
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Color
import java.awt.Font
import java.awt.Insets
import java.awt.Window
import javax.swing.JComponent

internal fun navBarItemBackground(selected: Boolean, focused: Boolean): Color {
  return if (selected && focused) {
    UIUtil.getListSelectionBackground(true)
  }
  else {
    UIUtil.getListBackground()
  }
}

internal fun  navBarItemForeground(selected: Boolean, focused: Boolean, inactive: Boolean): Color? {
  return if (StartupUiUtil.isUnderDarcula) {
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
    return RelativeFont.SMALL.derive(StartupUiUtil.labelFont)
  }
  return JBUI.CurrentTheme.StatusBar.font()
}

internal fun navBarItemInsets(): Insets {
  return if (ExperimentalUI.isNewUI()) {
    JBUI.insets("StatusBar.Breadcrumbs.itemBackgroundInsets",
                if (ExperimentalUI.isNewUI()) JBUI.insets(2, 4) else JBUI.insets(1))
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
    return JBUI.CurrentTheme.StatusBar.Breadcrumbs.itemInsets()
  }
}

internal fun navBarPopupOffset(firstItem: Boolean): Int {
  return if (firstItem) 0 else JBUIScale.scale(5)
}

internal fun trackCurrentWindow(panel: JComponent): StateFlow<Window?> {
  val window = MutableStateFlow<Window?>(null)
  UiNotifyConnector.installOn(panel, object : Activatable {

    override fun showNotify() {
      window.value = ComponentUtil.getWindow(panel)
    }

    override fun hideNotify() {
      window.value = null
    }
  }, false)
  return window
}
