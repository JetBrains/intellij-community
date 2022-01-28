// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

class DefaultDiffFileEditorCustomizer : DiffRequestProcessorEditorCustomizer {

  override fun customize(file: VirtualFile, editor: FileEditor, processor: DiffRequestProcessor) {
    var escapeHandler = file.getUserData(DiffVirtualFile.ESCAPE_HANDLER)
                        ?: DisposeDiffEditorEscapeAction(editor)
    if (escapeHandler !is DiffEditorEscapeAction) {
      escapeHandler = DiffEditorEscapeDelegatingAction(escapeHandler)
    }
    escapeHandler.registerCustomShortcutSet(CommonShortcuts.ESCAPE, editor.component, editor)
  }
}

private class DisposeDiffEditorEscapeAction(private val disposable: Disposable) : DumbAwareAction(), DiffEditorEscapeAction {
  override fun actionPerformed(e: AnActionEvent) = Disposer.dispose(disposable)
}

private class DiffEditorEscapeDelegatingAction(delegate: AnAction) : EmptyAction.MyDelegatingAction(delegate), DiffEditorEscapeAction
