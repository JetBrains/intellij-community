// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiJavaFile
import com.intellij.util.asSafely

internal class JavaLibraryUsageImportProcessor : LibraryUsageImportProcessor<PsiImportStatementBase> {
  override fun imports(file: PsiFile): List<PsiImportStatementBase> {
    return file.asSafely<PsiJavaFile>()
      ?.importList
      ?.allImportStatements
      ?.toList()
      .orEmpty()
  }

  override fun isSingleElementImport(import: PsiImportStatementBase): Boolean = !import.isOnDemand

  override fun importQualifier(import: PsiImportStatementBase): String? = import.importReference?.qualifiedName

  override fun resolve(import: PsiImportStatementBase): PsiElement? = import.resolve()
}
