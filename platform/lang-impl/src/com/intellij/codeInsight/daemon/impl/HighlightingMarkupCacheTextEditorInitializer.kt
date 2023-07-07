// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorInitializer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

private class HighlightingMarkupCacheTextEditorInitializer : TextEditorInitializer {
  override suspend fun init(project: Project, file: VirtualFile, document: Document, editorSupplier: suspend () -> EditorEx) {
    if (!HighlightingMarkupGrave.isEnabled()) {
      return
    }

    val markupGrave = project.serviceAsync<HighlightingMarkupGrave>()
    // is readAction required here?
    readActionBlocking {
      markupGrave.resurrectZombies(document, file)
    }
  }
}