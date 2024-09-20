// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.entities

import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.utils.splitIdentifierIntoTokens

open class IndexableClass(override val id: EntityId) : IndexableEntity {
  override val indexableRepresentation: String by lazy { splitIdentifierIntoTokens(id.id) }
}