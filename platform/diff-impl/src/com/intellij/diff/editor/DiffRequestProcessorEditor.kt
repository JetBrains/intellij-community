// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffEditorViewerListener
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.FileEditorWithTextEditors
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

@Suppress("LeakingThis")
open class DiffRequestProcessorEditor(
  private val file: VirtualFile,
  val processor: DiffRequestProcessor
) : DiffEditorBase(file,
                   processor.component,
                   processor), FileEditorWithTextEditors {

  init {
    processor.addListener(MyEditorViewerListener(), this)
  }

  override fun dispose() {
    val explicitDisposable = processor.getContextUserData(DiffUserDataKeysEx.DIFF_IN_EDITOR_WITH_EXPLICIT_DISPOSABLE)
    if (explicitDisposable != null) {
      explicitDisposable.run()
    }
    else {
      Disposer.dispose(processor)
    }

    super.dispose()
  }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    if (!Registry.`is`(DIFF_IN_NAVIGATION_HISTORY_KEY)) {
      return FileEditorState.INSTANCE
    }

    return processor.getState(level)
  }

  override fun setState(state: FileEditorState) {
    if (!Registry.`is`(DIFF_IN_NAVIGATION_HISTORY_KEY)) return

    processor.setState(state)
  }

  override fun getPreferredFocusedComponent(): JComponent? = processor.preferredFocusedComponent

  override fun selectNotify() {
    processor.fireProcessorActivated()
  }

  override fun getFilesToRefresh(): List<VirtualFile> = processor.filesToRefresh
  override fun getEmbeddedEditors(): List<Editor> = processor.embeddedEditors

  private inner class MyEditorViewerListener : DiffEditorViewerListener {
    override fun onActiveFileChanged() {
      val project = processor.project ?: return
      FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(file)
    }
  }
}

