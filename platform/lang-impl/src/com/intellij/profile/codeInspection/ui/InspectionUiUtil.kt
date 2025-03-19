// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui

import com.intellij.codeInspection.ui.InspectionOptionsPanel
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.border.Border

internal fun getBordersForOptions(optionsPanel: JComponent): Border {
  return if (optionsPanel is DialogPanel) JBUI.Borders.empty(12, 20, 0, 0)
  else JBUI.Borders.empty(10, 17, 0, 0)
}

@OptIn(IntellijInternalApi::class)
fun addScrollPaneIfNecessary(optionsPanel: JComponent): JComponent {
  return when (optionsPanel) {
    is JScrollPane -> optionsPanel
    is InspectionOptionsPanel -> ScrollPaneFactory.createScrollPane(optionsPanel.apply { addGlueIfNeeded() }, SideBorder.NONE)
    else -> ScrollPaneFactory.createScrollPane(optionsPanel, SideBorder.NONE)
  }
}