// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.context.NotebookDataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import javax.swing.JComponent

internal data class NotebookCellDataProvider(
  val editor: Editor,
  val component: JComponent,
  val intervalProvider: () -> NotebookCellLines.Interval,
) : DataProvider {
  override fun getData(key: String): Any? =
    when (key) {
      NotebookDataContext.NOTEBOOK_CELL_LINES_INTERVAL.name -> intervalProvider()
      PlatformCoreDataKeys.CONTEXT_COMPONENT.name -> component
      PlatformDataKeys.EDITOR.name -> editor
      else -> null
    }
}