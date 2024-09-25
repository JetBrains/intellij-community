// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.wrappers

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.indexer.IndexId.SYMBOLS
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettingsImpl
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class SymbolEmbeddingsStorageWrapper(project: Project, cs: CoroutineScope)
  : AbstractEmbeddingsStorageWrapper(project, SYMBOLS, cs) {
  override fun isEnabled(): Boolean = EmbeddingIndexSettingsImpl.getInstance().shouldIndexSymbols

  companion object {
    fun getInstance(project: Project): SymbolEmbeddingsStorageWrapper = project.service()
  }
}
