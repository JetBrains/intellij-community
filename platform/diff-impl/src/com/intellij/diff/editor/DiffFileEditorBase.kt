// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * @see [DiffRequestProcessorEditorCustomizer.Companion.customize]
 */
@ApiStatus.Internal
abstract class DiffFileEditorBase(
  private val file: VirtualFile,
  component: JComponent,
  private val contentDisposable: CheckedDisposable
) : FileEditorBase() {
  companion object {
    private val LOG = logger<DiffFileEditorBase>()
  }

  private val panel = MyPanel(component)

  init {
    Disposer.register(contentDisposable, Disposable {
      firePropertyChange(FileEditor.getPropValid(), true, false)
    })
  }

  override fun getComponent(): JComponent = panel

  override fun isValid(): Boolean = !isDisposed && !contentDisposable.isDisposed
  override fun getFile(): VirtualFile = file
  override fun getName(): String = DiffBundle.message("diff.file.editor.name")

  private inner class MyPanel(component: JComponent) : JPanel(BorderLayout()) {
    init {
      add(component, BorderLayout.CENTER)

      addContainerListener(object : ContainerAdapter() {
        override fun componentRemoved(e: ContainerEvent?) {
          if (isDisposed) return
          LOG.error("DiffRequestProcessor cannot be shown twice, see com.intellij.ide.actions.SplitAction.FORBID_TAB_SPLIT, file: $file")
        }
      })
    }
  }
}
