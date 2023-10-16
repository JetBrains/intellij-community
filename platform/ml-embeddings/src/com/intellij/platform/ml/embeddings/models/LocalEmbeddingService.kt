// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.models

import ai.grazie.emb.FloatTextEmbedding
import ai.grazie.emb.SuspendableTextEmbeddingsService
import ai.grazie.nlp.encoder.PreTrainedTextEncoder
import ai.grazie.utils.ki.TensorUtils
import io.kinference.ndarray.arrays.FloatNDArray
import kotlinx.coroutines.*

@OptIn(ExperimentalCoroutinesApi::class)
class LocalEmbeddingService(
  private val network: LocalEmbeddingNetwork,
  private val encoder: PreTrainedTextEncoder
): SuspendableTextEmbeddingsService<FloatTextEmbedding> {
  private val embeddingContext = Dispatchers.Default.limitedParallelism(1)

  suspend fun embed(texts: List<String>): List<FloatTextEmbedding> {
    return withContext(embeddingContext) {
      val tokenIds = encoder.encodeAsIds(texts, withSpecialTokens = true, maxLen = network.maxLen)
      val attentionMask = tokenIds.map { List(it.size) { 1 } }

      val tokensPadded = TensorUtils.create2DInt32Array(tokenIds, encoder.padId)
      val attentionMaskPadded = TensorUtils.create2DInt32Array(attentionMask, paddingIdx = 0)

      val embeddings: FloatNDArray = network.predict(tokensPadded, attentionMaskPadded)
      embeddings.split(parts = embeddings.shape[0], axis = 0)
        .map { FloatTextEmbedding((it as FloatNDArray).array.toArray()) }
    }
  }

  override suspend fun embed(texts: List<String>, model: String?): List<FloatTextEmbedding> {
    return embed(texts)
  }
}
