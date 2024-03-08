// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.logging.EmbeddingSearchLogger
import com.intellij.platform.ml.embeddings.search.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.platform.ml.embeddings.utils.generateEmbedding
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.*

abstract class DiskSynchronizedEmbeddingsStorage<T : IndexableEntity>(val project: Project,
                                                                      private val cs: CoroutineScope) : EmbeddingsStorage {
  abstract val index: DiskSynchronizedEmbeddingSearchIndex

  internal abstract val reportableIndex: EmbeddingSearchLogger.Index

  @RequiresBackgroundThread
  override suspend fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double?): List<ScoredText> {
    FileBasedEmbeddingStoragesManager.getInstance(project).triggerIndexing()
    if (index.size == 0) return emptyList()
    val searchStartTime = System.nanoTime()
    val embedding = generateEmbedding(text) ?: return emptyList()
    val neighbours = index.findClosest(embedding, topK, similarityThreshold)
    EmbeddingSearchLogger.searchFinished(project, reportableIndex, TimeoutUtil.getDurationMillis(searchStartTime))
    return neighbours
  }

  @RequiresBackgroundThread
  suspend fun streamSearchNeighbours(text: String, similarityThreshold: Double? = null): Sequence<ScoredText> {
    FileBasedEmbeddingStoragesManager.getInstance(project).triggerIndexing()
    if (index.size == 0) return emptySequence()
    val embedding = generateEmbedding(text) ?: return emptySequence()
    return index.streamFindClose(embedding, similarityThreshold)
  }
}
