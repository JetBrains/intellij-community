// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorInitializer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class HighlighterTextEditorInitializer : TextEditorInitializer {
  override suspend fun initializeEditor(project: Project, file: VirtualFile, document: Document, editorSupplier: suspend () -> EditorEx) {
    TextEditorImpl.setHighlighterToEditor(project, file, document, editorSupplier)
    // invoke restoreCachedHighlightingFromDisk only after the highlighter was set to the editor, to avoid showing range highlighters in an incorrect color scheme
    restoreCachedHighlightingFromDisk(project, document, file)
  }

  private suspend fun restoreCachedHighlightingFromDisk(project: Project,
                                                        document: Document,
                                                        file: VirtualFile) {
    if (HighlightingMarkupGrave.isEnabled()) {
      val markupGrave = project.serviceAsync<HighlightingMarkupGrave>()
      markupGrave.resurrectZombies(document, file)
    }
  }
}