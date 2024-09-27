// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.searcher

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettings

internal interface EmbeddingEntitiesIndexer : Disposable {
  suspend fun index(project: Project, settings: EmbeddingIndexSettings)
}