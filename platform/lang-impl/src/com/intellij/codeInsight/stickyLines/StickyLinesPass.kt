// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.stickyLines

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.openapi.editor.Document
import com.intellij.ui.components.breadcrumbs.StickyLineInfo
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesCollector
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile

internal class StickyLinesPass(
  private val document: Document,
  private val psiFile: PsiFile,
) : TextEditorHighlightingPass(psiFile.project, document), DumbAware {

  private val collector = StickyLinesCollector(psiFile.project, document)
  private var collectedLines: Collection<StickyLineInfo>? = null

  override fun doCollectInformation(progress: ProgressIndicator) {
    FileDocumentManager.getInstance().getFile(document)?.let { vFile ->
      collectedLines = collector.collectLines(vFile, progress)
    }
  }

  override fun doApplyInformationToEditor() {
    collectedLines?.let { lines ->
      collector.applyLines(psiFile, lines)
    }
    collectedLines = null
  }
}
