// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
public interface LibraryUsageImportProcessor<T : PsiElement> {
  public fun imports(file: PsiFile): List<T>

  /**
   * Checks if the statement represents a single element or on-demand import.
   *
   * @return true if the import statement is a single element, false otherwise.
   */
  public fun isSingleElementImport(import: T): Boolean
  public fun importQualifier(import: T): String?
  public fun resolve(import: T): PsiElement?
}