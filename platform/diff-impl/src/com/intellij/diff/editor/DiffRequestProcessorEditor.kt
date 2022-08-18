// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffRequestProcessorListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

@Suppress("LeakingThis")
open class DiffRequestProcessorEditor(
  private val file: DiffVirtualFile,
  val processor: DiffRequestProcessor
) : DiffEditorBase(file,
                   processor.component,
                   processor,
                   processor.context) {

  init {
    processor.addListener(MyProcessorListener(), this)
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
}
