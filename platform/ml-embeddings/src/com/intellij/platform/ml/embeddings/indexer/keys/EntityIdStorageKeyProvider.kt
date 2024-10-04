// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.keys

import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableEntity

class EntityIdStorageKeyProvider : EmbeddingStorageKeyProvider<EntityId> {
  override suspend fun findKey(project: Project?, indexId: IndexId, entity: IndexableEntity): EntityId {
    return entity.id
  }

  override suspend fun findEntityId(project: Project?, indexId: IndexId, key: EntityId): String? {
    return key.id
  }
}