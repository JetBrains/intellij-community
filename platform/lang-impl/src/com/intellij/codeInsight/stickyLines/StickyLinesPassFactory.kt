// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.stickyLines

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesCollector
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

internal class StickyLinesPassFactory : TextEditorHighlightingPassFactoryRegistrar, TextEditorHighlightingPassFactory, DumbAware {

  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

  override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass? {
    if (editor.project != null && !editor.isDisposed && editor.settings.areStickyLinesShown()) {
      if (StickyLinesCollector.ModStamp.isChanged(editor, psiFile)) {
        return StickyLinesPass(editor.document, psiFile)
      }
    }
    return null
  }
}
