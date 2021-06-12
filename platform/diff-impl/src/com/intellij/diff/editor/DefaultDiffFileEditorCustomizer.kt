// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

class DefaultDiffFileEditorCustomizer : DiffRequestProcessorEditorCustomizer {

  override fun customize(file: VirtualFile, editor: FileEditor, processor: DiffRequestProcessor) {
    if (editor !is FileEditorBase) return

    if (!DiffUtil.isUserDataFlagSet(DiffUserDataKeysEx.DIFF_IN_EDITOR_WITH_EXPLICIT_DISPOSABLE, processor.context)) {
      Disposer.register(editor, Disposable {
        Disposer.dispose(processor)
      })
    }
    Disposer.register(processor, Disposable {
      editor.firePropertyChange(FileEditor.PROP_VALID, true, false)
    })

    processor.component.registerKeyboardAction({ Disposer.dispose(editor) },
                                               KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)

    file.getUserData(DiffVirtualFile.ESCAPE_HANDLER)?.registerCustomShortcutSet(CommonShortcuts.ESCAPE, editor.component, editor)
  }
}
