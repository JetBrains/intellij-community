// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.DiffContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.docking.impl.DockManagerImpl

class DefaultDiffFileEditorCustomizer : DiffRequestProcessorEditorCustomizer {

  override fun customize(file: VirtualFile, editor: FileEditor, context: DiffContext) {
    var escapeHandler = file.getUserData(DiffVirtualFileBase.ESCAPE_HANDLER)
                        ?: DisposeDiffEditorEscapeAction(editor)
    if (escapeHandler !is DiffEditorEscapeAction) {
      escapeHandler = DiffEditorEscapeDelegatingAction(escapeHandler)
    }
    escapeHandler.registerCustomShortcutSet(CommonShortcuts.ESCAPE, editor.component, editor)

    editor.putUserData(EditorWindow.HIDE_TABS, true)
    editor.putUserData(FileEditorManagerImpl.SINGLETON_EDITOR_IN_WINDOW, true)
    editor.putUserData(DockManagerImpl.SHOW_NORTH_PANEL, false)
  }
}

private class DisposeDiffEditorEscapeAction(private val disposable: Disposable) : DumbAwareAction(), DiffEditorEscapeAction {
  override fun actionPerformed(e: AnActionEvent) = Disposer.dispose(disposable)
}

private class DiffEditorEscapeDelegatingAction(delegate: AnAction) : AnActionWrapper(delegate), DiffEditorEscapeAction
