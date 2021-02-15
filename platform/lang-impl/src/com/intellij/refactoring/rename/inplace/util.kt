// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.template.TemplateResultListener
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.PsiRenameUsage
import com.intellij.util.DocumentUtil

internal fun usageRangeInHost(hostFile: PsiFile, usage: PsiRenameUsage): TextRange? {
  return if (usage.file == hostFile) {
    usage.range
  }
  else {
    injectedToHost(hostFile.project, usage.file, usage.range)
  }
}

private fun injectedToHost(project: Project, injectedFile: PsiFile, injectedRange: TextRange): TextRange? {
  val injectedDocument: DocumentWindow = PsiDocumentManager.getInstance(project).getDocument(injectedFile) as? DocumentWindow
                                         ?: return null
  val startOffsetHostRange: TextRange = injectedDocument.getHostRange(injectedDocument.injectedToHost(injectedRange.startOffset))
                                        ?: return null
  val endOffsetHostRange: TextRange = injectedDocument.getHostRange(injectedDocument.injectedToHost(injectedRange.endOffset))
                                      ?: return null
  return if (startOffsetHostRange == endOffsetHostRange) {
    injectedDocument.injectedToHost(injectedRange)
  }
  else {
    null
  }
}

internal fun TemplateState.addTemplateResultListener(resultConsumer: (TemplateResultListener.TemplateResult) -> Unit) {
  return addTemplateStateListener(TemplateResultListener(resultConsumer))
}

internal fun deleteInplaceTemplateSegments(
  project: Project,
  document: Document,
  templateSegmentRanges: List<TextRange>
): Runnable {
  val hostDocumentContent = document.text
  val stateBefore: List<Pair<RangeMarker, String>> = templateSegmentRanges.map { range ->
    val marker = document.createRangeMarker(range).also {
      it.isGreedyToRight = true
    }
    val content = range.substring(hostDocumentContent)
    Pair(marker, content)
  }

  DocumentUtil.executeInBulk(document, true) {
    for (range in templateSegmentRanges.asReversed()) {
      document.deleteString(range.startOffset, range.endOffset)
    }
  }

  return Runnable {
    WriteCommandAction.writeCommandAction(project).run<Throwable> {
      DocumentUtil.executeInBulk(document, true) {
        for ((marker: RangeMarker, content: String) in stateBefore) {
          document.replaceString(marker.startOffset, marker.endOffset, content)
        }
      }
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }
}
