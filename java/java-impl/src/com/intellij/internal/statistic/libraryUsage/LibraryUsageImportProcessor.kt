// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

interface LibraryUsageImportProcessor<T : PsiElement> {
  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<LibraryUsageImportProcessor<PsiElement>>(
      "com.intellij.internal.statistic.libraryUsage.libraryUsageImportProcessor"
    )
  }

  fun isApplicable(fileType: FileType): Boolean
  fun imports(file: PsiFile): List<T>

  /**
   * Checks if the statement represents a single element or on-demand import.
   *
   * @return true if the import statement is a single element, false otherwise.
   */
  fun isSingleElementImport(import: T): Boolean
  fun importQualifier(import: T): String?
  fun resolve(import: T): PsiElement?
}