// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.MarkupGraveSuppressor
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorInitializer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId

private class HighlighterTextEditorInitializer : TextEditorInitializer {
  override suspend fun initializeEditor(project: Project,
                                        file: VirtualFile,
                                        document: Document,
                                        editorSupplier: suspend () -> EditorEx,
                                        highlighterReady: suspend () -> Unit) {
    if (!HighlightingMarkupGrave.isEnabled()
        || project.service<MarkupGraveSuppressor>().shouldSuppress(file, document)
        || file !is VirtualFileWithId) {
      return
    }

    val markupGrave = project.serviceAsync<HighlightingMarkupGrave>()

    // we have to make sure that editor highlighter is created before we start raising zombies
    // because creation of highlighter has side effect that TextAttributesKey.ourRegistry is filled with corresponding keys
    // (e.g. class loading of org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors)
    // without such guarantee there is a risk to get uninitialized fallbackKey in TextAttributesKey.find(externalName)
    // it may lead to incorrect color of highlighters on startup
    highlighterReady()

    readActionBlocking {
      markupGrave.resurrectZombies(document, file)
    }
  }
}