// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.endInlay.addToolbar

import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isOrdinaryNotebookEditor
import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.ui.endInlay.EditorNotebookEndInlay
import com.intellij.notebooks.visualization.ui.endInlay.EditorNotebookEndInlayProvider

class EditorNotebookEndAddToolbarProvider : EditorNotebookEndInlayProvider {
  override fun create(inlayManager: NotebookCellInlayManager): EditorNotebookEndInlay? {
    if (!inlayManager.editor.isOrdinaryNotebookEditor())
      return null
    return EditorNotebookEndAddToolbar(inlayManager)
  }
}