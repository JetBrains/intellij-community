// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.codeHighlighting.MainHighlightingPassFactory
import com.intellij.codeHighlighting.Pass
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile

internal class LspHighlightingPassFactory : MainHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar, DumbAware {

  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    LspHighlightingApplier.GROUP_ID = registrar.registerTextEditorHighlightingPass(
      this,
      null, // runAfterCompletionOf
      intArrayOf(Pass.UPDATE_ALL), // runAfterStartingOf
      false, // runIntentionsPassAfter
      -1, // forcedPassId
    )
  }

  override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass? {
    val file = psiFile.virtualFile ?: return null
    if (!file.isInLocalFileSystem) return null

    val currentStamp = psiFile.manager.modificationTracker.modificationCount
    if (editor.getUserData(PSI_MODIFICATION_STAMP) == currentStamp) return null

    return LspHighlightingPass(psiFile.project, editor.document, psiFile, file, editor)
  }

  override fun createMainHighlightingPass(
    psiFile: PsiFile,
    document: Document,
    highlightInfoProcessor: HighlightInfoProcessor,
  ): TextEditorHighlightingPass? {
    // For read-only / batch analysis (e.g., "Inspect Code")
    val file = psiFile.virtualFile ?: return null
    if (!file.isInLocalFileSystem) return null
    return LspHighlightingPass(psiFile.project, document, psiFile, file, editor = null)
  }
}

internal val PSI_MODIFICATION_STAMP = Key.create<Long>("lsp.highlighting.psi.modification.stamp")
