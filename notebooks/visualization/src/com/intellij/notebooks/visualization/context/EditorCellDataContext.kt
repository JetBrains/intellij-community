// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.context

import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import javax.swing.JComponent

object EditorCellDataContext {
  fun createContextProvider(editorCell: EditorCell, component: JComponent): JComponent {
    return UiDataProvider.wrapComponent(component) { sink ->
      sink[NotebookDataContext.NOTEBOOK_CELL_LINES_INTERVAL] = editorCell.intervalOrNull
      sink[NotebookDataContext.SHOW_TEXT] = true
      sink[PlatformCoreDataKeys.CONTEXT_COMPONENT] = component
      sink[PlatformDataKeys.EDITOR] = editorCell.editor
    }
  }
}