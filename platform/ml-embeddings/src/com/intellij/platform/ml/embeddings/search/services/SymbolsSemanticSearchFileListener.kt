// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.search.settings.SemanticSearchSettings

@Service(Service.Level.PROJECT)
class SymbolsSemanticSearchFileListener(project: Project): SemanticSearchFileContentChangeListener<IndexableSymbol>(project) {
  override val isEnabled: Boolean
    get() = SemanticSearchSettings.getInstance().enabledInSymbolsTab

  override fun getStorage() = SymbolEmbeddingStorage.getInstance(project)

  override fun getEntity(id: String) = IndexableSymbol(id.intern())

  companion object {
    fun getInstance(project: Project): SymbolsSemanticSearchFileListener = project.service()
  }
}