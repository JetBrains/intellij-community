// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffRequestProcessorListener
import com.intellij.diff.tools.combined.editors
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorWithTextEditors
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.Disposer
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
    processor.addListener(MyProcessorListener(), this)
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

  override fun getPreferredFocusedComponent(): JComponent? = processor.preferredFocusedComponent

  override fun selectNotify() {
    processor.updateRequest()
  }

  override fun getFilesToRefresh(): List<VirtualFile> = processor.activeRequest?.filesToRefresh ?: emptyList()

  private inner class MyProcessorListener : DiffRequestProcessorListener {
    override fun onViewerChanged() {
      val project = processor.project ?: return
      FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(file)
    }
  }

  override fun getEmbeddedEditors(): List<Editor> {
    return processor.activeViewer?.editors.orEmpty()
  }
}
