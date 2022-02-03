// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiJavaFile
import com.intellij.util.castSafelyTo

class JavaLibraryUsageImportProcessor : LibraryUsageImportProcessor<PsiImportStatementBase> {
  override fun isApplicable(fileType: FileType): Boolean = fileType == JavaFileType.INSTANCE
  override fun imports(file: PsiFile): List<PsiImportStatementBase> = file.castSafelyTo<PsiJavaFile>()
    ?.importList
    ?.allImportStatements
    ?.toList()
    .orEmpty()

  override fun isSingleElementImport(import: PsiImportStatementBase): Boolean = !import.isOnDemand
  override fun importQualifier(import: PsiImportStatementBase): String? = import.importReference?.qualifiedName
  override fun resolve(import: PsiImportStatementBase): PsiElement? = import.resolve()
}
