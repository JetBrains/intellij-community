// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.border.Border

fun getBordersForOptions(optionsPanel: JComponent): Border {
  return if (optionsPanel is DialogPanel) JBUI.Borders.empty(12, 20, 0, 0)
  else JBUI.Borders.empty(10, 17, 0, 0)
}

fun addScrollPaneIfNecessary(optionsPanel: JComponent): JComponent {
  return if (UIUtil.hasScrollPane(optionsPanel)) optionsPanel
  else ScrollPaneFactory.createScrollPane(optionsPanel, SideBorder.NONE)
}