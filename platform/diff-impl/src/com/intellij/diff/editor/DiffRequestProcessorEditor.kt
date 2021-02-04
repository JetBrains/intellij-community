// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.awt.BorderLayout
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
import javax.swing.JPanel
import javax.swing.KeyStroke

open class DiffRequestProcessorEditor(
  private val file: DiffVirtualFile,
  val processor: DiffRequestProcessor
) : FileEditorBase() {
  companion object {
    private val LOG = logger<DiffRequestProcessorEditor>()
  }

  private var disposed = false

  private val panel = MyPanel(processor.component)

  init {
    putUserData(EditorWindow.HIDE_TABS, true)
    if (!DiffUtil.isUserDataFlagSet(DiffUserDataKeysEx.DIFF_IN_EDITOR_WITH_EXPLICIT_DISPOSABLE, processor.context)) {
      Disposer.register(this, Disposable {
        Disposer.dispose(processor)
      })
    }
    Disposer.register(processor, Disposable {
      propertyChangeSupport.firePropertyChange(FileEditor.PROP_VALID, true, false)
    })

    processor.component.registerKeyboardAction({ Disposer.dispose(this) },
                                               KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_IN_FOCUSED_WINDOW)
  }

  override fun getComponent(): JComponent = panel
  override fun getPreferredFocusedComponent(): JComponent? = processor.preferredFocusedComponent

  override fun dispose() {
    disposed = true
  }
  override fun isValid(): Boolean = !disposed && !processor.isDisposed
  override fun getFile(): VirtualFile = file
  override fun getName(): String = DiffBundle.message("diff.file.editor.name")

  override fun selectNotify() {
    processor.updateRequest()
  }

  private inner class MyPanel(component: JComponent) : JPanel(BorderLayout()) {
    init {
      add(component, BorderLayout.CENTER)

      addContainerListener(object : ContainerAdapter() {
        override fun componentRemoved(e: ContainerEvent?) {
          if (disposed) return
          LOG.error("DiffRequestProcessor cannot be shown twice, see com.intellij.ide.actions.SplitAction.FORBID_TAB_SPLIT, file: $file")
        }
      })
    }
  }
}
