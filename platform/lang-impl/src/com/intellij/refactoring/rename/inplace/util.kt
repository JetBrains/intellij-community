// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.template.TemplateResultListener
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.PsiRenameUsage

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
