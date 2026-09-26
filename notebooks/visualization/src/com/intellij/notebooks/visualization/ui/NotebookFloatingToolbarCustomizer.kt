// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

class NotebookFloatingToolbarCustomizer : TextEditorCustomizer {
  override fun customize(textEditor: TextEditor, coroutineScope: CoroutineScope) {
    if (!shouldAcceptEditor(textEditor)) {
      return
    }

    val toolbarScope = coroutineScope.childScope("MarkdownCellsFloatingToolbar")
    var registered = false
    try {
      val toolbar = MarkdownCellsFloatingToolbar(editor = textEditor.editor, coroutineScope = toolbarScope)
      registered = Disposer.tryRegister(textEditor, toolbar)
      if (!registered) {
        Disposer.dispose(toolbar)
      }
    }
    finally {
      if (!registered) {
        toolbarScope.cancel()
      }
    }
  }

  private fun shouldAcceptEditor(editor: TextEditor): Boolean {
    return Registry.`is`("jupyter.markdown.cells.floating.toolbar") && editor.file.fileType.name == "Jupyter"
  }
}