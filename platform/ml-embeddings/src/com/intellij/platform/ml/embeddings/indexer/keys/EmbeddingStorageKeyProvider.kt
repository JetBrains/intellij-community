// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.keys

import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableEntity

interface EmbeddingStorageKeyProvider<KeyT> {
  /**
   * Finds a key to store entity in the embedding storage
   */
  suspend fun findKey(project: Project?, indexId: IndexId, entity: IndexableEntity): KeyT

  /**
   * Finds identifier that could be used to retrieve PsiElement
   */
  suspend fun findEntityId(project: Project?, indexId: IndexId, key: KeyT): String
}