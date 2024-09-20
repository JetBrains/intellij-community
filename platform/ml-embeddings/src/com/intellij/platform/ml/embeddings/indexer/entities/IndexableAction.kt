// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.entities

import com.intellij.platform.ml.embeddings.jvm.indices.EntityId

data class IndexableAction(val actionId: EntityId, val templateText: String): IndexableEntity {
  override val id: EntityId
    get() = actionId
  override val indexableRepresentation: String
    get() = templateText
}