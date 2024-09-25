// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeExtension
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableSymbol
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.toArray
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface SymbolsProvider {
  fun extract(file: PsiFile): List<IndexableSymbol>

  companion object {
    private val EXTENSION = FileTypeExtension<SymbolsProvider>("com.intellij.embeddings.indexer.symbolsProvider")

    val supportedFileTypes: Array<FileType>
      get() = EXTENSION.getAllRegisteredExtensions().keys.toArray(FileType.EMPTY_ARRAY)

    @RequiresReadLock
    fun extractSymbols(file: PsiFile): List<IndexableSymbol> {
      ThreadingAssertions.assertReadAccess() // annotation doesn't work in Kotlin
      return EXTENSION.forFileType(file.fileType)?.extract(file) ?: emptyList()
    }
  }
}