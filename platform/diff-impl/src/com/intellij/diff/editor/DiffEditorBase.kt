// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.diff.DiffContext
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.awt.BorderLayout
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("LeakingThis")
abstract class DiffEditorBase(
  private val file: DiffVirtualFileBase,
  component: JComponent,
  private val disposable: CheckedDisposable,
  private val context: DiffContext
) : FileEditorBase() {
  companion object {
    private val LOG = logger<DiffEditorBase>()
  }

  private val panel = MyPanel(component)

  init {
    Disposer.register(disposable, Disposable {
      firePropertyChange(FileEditor.PROP_VALID, true, false)
    })

    DiffRequestProcessorEditorCustomizer.customize(file, this, context)
  }

  override fun getComponent(): JComponent = panel

  override fun dispose() {
    if (!DiffUtil.isUserDataFlagSet(DiffUserDataKeysEx.DIFF_IN_EDITOR_WITH_EXPLICIT_DISPOSABLE, context)) {
      Disposer.dispose(disposable)
    }
    super.dispose()
  }

  override fun isValid(): Boolean = !isDisposed && !disposable.isDisposed
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
