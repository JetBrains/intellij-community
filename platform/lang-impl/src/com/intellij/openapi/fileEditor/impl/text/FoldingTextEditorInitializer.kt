// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class FoldingTextEditorInitializer : TextEditorInitializer {
  override suspend fun initializeEditor(project: Project, file: VirtualFile, document: Document, editorSupplier: suspend () -> EditorEx) {
    if (project.isDefault) {
      return
    }

    val codeFoldingManager = project.serviceAsync<CodeFoldingManager>()
    val foldingState = readAction {
      if (PsiDocumentManager.getInstance(project).isCommitted(document)) {
        catchingExceptions {
          blockingContextToIndicator {
            codeFoldingManager.buildInitialFoldings(document)
          }
        }
      }
      else {
        null
      }
    } ?: return

    val editor = editorSupplier()
    withContext(Dispatchers.EDT) {
      foldingState.setToEditor(editor)
    }
  }
}