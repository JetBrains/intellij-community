// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

class DefaultDiffFileEditorCustomizer : DiffRequestProcessorEditorCustomizer {

  override fun customize(file: VirtualFile, editor: FileEditor, processor: DiffRequestProcessor) {
    val escapeHandler = file.getUserData(DiffVirtualFile.ESCAPE_HANDLER)
                        ?: DumbAwareAction.create { Disposer.dispose(editor) }
    escapeHandler.registerCustomShortcutSet(CommonShortcuts.ESCAPE, editor.component, editor)
  }
}
