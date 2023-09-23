// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

class CombinedDiffComponentEditor(
  private val file: VirtualFile,
  val processor: CombinedDiffComponentFactory
) : FileEditorBase() {

  init {
    Disposer.register(processor.ourDisposable, Disposable {
      firePropertyChange(FileEditor.PROP_VALID, true, false)
    })
  }

  override fun dispose() {
    Disposer.dispose(processor.ourDisposable)
    super.dispose()
  }

  override fun getComponent(): JComponent = processor.getMainComponent()
  override fun getPreferredFocusedComponent(): JComponent? = processor.getPreferredFocusedComponent()

  override fun isValid(): Boolean = !isDisposed && !processor.ourDisposable.isDisposed
  override fun getFile(): VirtualFile = file
  override fun getName(): String = DiffBundle.message("diff.file.editor.name")
}
