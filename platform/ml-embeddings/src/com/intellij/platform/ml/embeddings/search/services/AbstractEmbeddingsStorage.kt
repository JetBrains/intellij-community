// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

abstract class AbstractEmbeddingsStorage {

  @RequiresBackgroundThread
  abstract suspend fun searchNeighboursIfEnabled(text: String, topK: Int, similarityThreshold: Double? = null): List<ScoredText>

  @RequiresBackgroundThread
  abstract suspend fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double? = null): List<ScoredText>
}