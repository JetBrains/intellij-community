// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.indices

import com.intellij.openapi.application.readAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.ml.embeddings.search.services.IndexableClass
import com.intellij.platform.ml.embeddings.search.services.IndexableSymbol
import com.intellij.psi.PsiFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

interface FileIndexableEntitiesProvider {
  fun extractIndexableSymbols(file: PsiFile): List<IndexableSymbol>

  fun extractIndexableClasses(file: PsiFile): List<IndexableClass>

  companion object {
    private val EP_NAME: ExtensionPointName<FileIndexableEntitiesProvider> =
      ExtensionPointName.create("com.intellij.searcheverywhere.ml.fileIndexableEntitiesProvider")

    fun extractSymbols(file: PsiFile): Flow<IndexableSymbol> {
      return channelFlow {
        for (extension in EP_NAME.extensionList) {
          launch {
            readAction { extension.extractIndexableSymbols(file) }.forEach {
              send(it)
            }
          }
        }
      }
    }

    fun extractClasses(file: PsiFile): Flow<IndexableClass> {
      return channelFlow {
        for (extension in EP_NAME.extensionList) {
          launch {
            readAction { extension.extractIndexableClasses(file) }.forEach {
              send(it)
            }
          }
        }
      }
    }
  }
}