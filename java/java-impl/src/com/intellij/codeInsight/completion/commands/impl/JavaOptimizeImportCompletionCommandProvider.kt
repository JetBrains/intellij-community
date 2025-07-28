// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractOptimizeImportCompletionCommandProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.parentOfType

public class JavaOptimizeImportCompletionCommandProvider : AbstractOptimizeImportCompletionCommandProvider() {
  override fun isImportList(psiFile: PsiFile, offset: Int): Boolean {
    if (offset - 1 < 0) return false
    val element = psiFile.findElementAt(offset - 1)
    return element?.parentOfType<PsiImportList>(withSelf = true) != null
  }

  override fun getTextRangeImportList(psiFile: PsiFile, offset: Int): TextRange? {
    if (psiFile is PsiJavaFile) return psiFile.importList?.textRange
    return null
  }
}