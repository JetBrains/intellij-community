// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.indices

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.ml.embeddings.search.services.IndexableClass
import com.intellij.platform.ml.embeddings.search.services.IndexableSymbol
import com.intellij.psi.PsiFile

interface FileIndexableEntitiesProvider {
  fun extractIndexableSymbols(file: PsiFile): List<IndexableSymbol>

  fun extractIndexableClasses(file: PsiFile): List<IndexableClass>

  companion object {
    val EP_NAME: ExtensionPointName<FileIndexableEntitiesProvider> =
      ExtensionPointName.create("com.intellij.searcheverywhere.ml.fileIndexableEntitiesProvider")

    fun extractSymbols(file: PsiFile): List<IndexableSymbol> {
      for (extension in EP_NAME.extensionList) {
        val symbols = extension.extractIndexableSymbols(file)
        if (symbols.isNotEmpty()) return symbols
      }
      return emptyList()
    }

    fun extractClasses(file: PsiFile): List<IndexableClass> {
      for (extension in EP_NAME.extensionList) {
        val classes = extension.extractIndexableClasses(file)
        if (classes.isNotEmpty()) return classes
      }
      return emptyList()
    }
  }
}