// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.execution.ShortenCommandLine
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import javax.swing.ListCellRenderer

internal fun createRenderer(): ListCellRenderer<ShortenCommandLine?> {
  return listCellRenderer("") {
    text(value.presentableName)
    gap(LcrRow.Gap.NONE)
    text(" - " + value.description) {
      foreground = greyForeground
    }
  }
}
