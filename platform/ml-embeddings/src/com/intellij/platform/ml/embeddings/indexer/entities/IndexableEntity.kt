// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.entities

import com.intellij.platform.ml.embeddings.jvm.indices.EntityId

interface IndexableEntity {
  val id: EntityId
  val indexableRepresentation: String
}