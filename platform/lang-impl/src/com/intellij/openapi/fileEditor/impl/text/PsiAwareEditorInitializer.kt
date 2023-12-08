// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.codeInsight.documentation.render.DocRenderManager
import com.intellij.codeInsight.documentation.render.DocRenderPassFactory
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class DocRenderTextEditorInitializer : TextEditorInitializer {
  override suspend fun initializeEditor(project: Project,
                                        file: VirtualFile,
                                        document: Document,
                                        editorSupplier: suspend () -> EditorEx,
                                        highlighterReady: suspend () -> Unit) {
    val editor = editorSupplier.invoke()
    if (!DocRenderManager.isDocRenderingEnabled(editor)) {
      return
    }

    val psiManager = project.serviceAsync<PsiManager>()
    val items = readAction {
      val psiFile = psiManager.findFile(file) ?: return@readAction null
      DocRenderPassFactory.calculateItemsToRender(editor, psiFile)
    } ?: return

    withContext(Dispatchers.EDT) {
      DocRenderPassFactory.applyItemsToRender(editor, project, items, true)
    }
  }
}

private class FocusZoneTextEditorInitializer : TextEditorInitializer {
  override suspend fun initializeEditor(project: Project,
                                        file: VirtualFile,
                                        document: Document,
                                        editorSupplier: suspend () -> EditorEx,
                                        highlighterReady: suspend () -> Unit) {
    if (!FocusModePassFactory.isEnabled()) {
      return
    }

    val psiManager = project.serviceAsync<PsiManager>()
    val focusZones = readAction {
      val psiFile = psiManager.findFile(file)
      FocusModePassFactory.calcFocusZones(psiFile)
    } ?: return

    val editor = editorSupplier()
    withContext(Dispatchers.EDT) {
      FocusModePassFactory.setToEditor(focusZones, editor)
      if (editor is EditorImpl) {
        editor.applyFocusMode()
      }
    }
  }
}
