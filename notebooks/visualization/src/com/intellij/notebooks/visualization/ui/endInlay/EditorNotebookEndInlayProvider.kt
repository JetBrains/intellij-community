// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.endInlay

import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer

interface EditorNotebookEndInlayProvider {
  fun create(inlayManager: NotebookCellInlayManager): EditorNotebookEndInlay?

  companion object {
    private val EP = ExtensionPointName.create<EditorNotebookEndInlayProvider>("org.jetbrains.plugins.notebooks.editorNotebookEndInlayProvider")

    fun create(inlayManager: NotebookCellInlayManager): List<EditorNotebookEndInlay> {
      return EP.extensionsIfPointIsRegistered.mapNotNull {
        val endInlay = it.create(inlayManager) ?: return@mapNotNull null
        Disposer.register(inlayManager, endInlay)
        endInlay
      }
    }
  }
}