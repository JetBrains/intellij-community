// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.coroutineScope

class NotebookFloatingToolbarCustomizer : TextEditorCustomizer {
  override suspend fun execute(textEditor: TextEditor) {
    if (shouldAcceptEditor(textEditor)) {
      coroutineScope {
        val toolbar = MarkdownCellsFloatingToolbar(editor = textEditor.editor, coroutineScope = this)
        Disposer.register(textEditor, toolbar)
      }
    }
  }

  private fun shouldAcceptEditor(editor: TextEditor): Boolean {
    if (!Registry.`is`("jupyter.markdown.cells.floating.toolbar")) return false
    return editor.file.fileType.name == "Jupyter"
  }
}