// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.indices

import com.intellij.platform.ml.embeddings.indexer.entities.IndexableEntity

data class EntityIndexAction(val entity: IndexableEntity, val actionType: EntityActionType, val sourceType: EntitySourceType)
