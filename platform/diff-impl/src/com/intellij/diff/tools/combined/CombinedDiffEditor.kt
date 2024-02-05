// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.editor.DiffEditorBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.FileEditorWithTextEditors
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import javax.swing.JComponent

class CombinedDiffEditor(file: CombinedDiffVirtualFile, val processor: CombinedDiffComponentProcessor) :
  DiffEditorBase(file, processor.getMainComponent(), processor.disposable), FileEditorWithTextEditors {

  override fun dispose() {
    Disposer.dispose(processor.disposable)
    super.dispose()
  }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    if (!Registry.`is`(DIFF_IN_NAVIGATION_HISTORY_KEY)) return FileEditorState.INSTANCE

    return processor.getState(level)
  }

  override fun setState(state: FileEditorState) {
    processor.setState(state)
  }

  override fun getPreferredFocusedComponent(): JComponent? = processor.getPreferredFocusedComponent()

  override fun getEmbeddedEditors(): List<Editor> {
    return processor.context.getUserData(COMBINED_DIFF_VIEWER_KEY)?.editors.orEmpty()
  }
}
