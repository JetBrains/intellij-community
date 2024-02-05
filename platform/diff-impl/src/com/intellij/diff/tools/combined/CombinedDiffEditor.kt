// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.editor.DiffEditorBase
import com.intellij.diff.impl.DiffEditorViewerListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.FileEditorWithTextEditors
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

class CombinedDiffEditor(file: CombinedDiffVirtualFile, val processor: CombinedDiffComponentProcessor) :
  DiffEditorBase(file, processor.component, processor.disposable), FileEditorWithTextEditors {

  init {
    processor.addListener(MyEditorViewerListener(), this)
  }

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

  override fun getPreferredFocusedComponent(): JComponent? = processor.preferredFocusedComponent

  override fun getFilesToRefresh(): List<VirtualFile> = processor.filesToRefresh
  override fun getEmbeddedEditors(): List<Editor> = processor.embeddedEditors

  private inner class MyEditorViewerListener : DiffEditorViewerListener {
    override fun onActiveFileChanged() {
      val project = processor.context.project ?: return
      FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(file)
    }
  }
}
