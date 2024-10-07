// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.models

import ai.grazie.emb.FloatTextEmbedding
import ai.grazie.emb.SuspendableTextEmbeddingsService
import ai.grazie.nlp.encoder.PreTrainedTextEncoder
import io.kinference.ndarray.arrays.FloatNDArray
import io.kinference.ndarray.arrays.IntNDArray

class LocalEmbeddingService(
  private val network: LocalEmbeddingNetwork,
  private val encoder: PreTrainedTextEncoder
) : SuspendableTextEmbeddingsService<FloatTextEmbedding> {
  suspend fun embed(texts: List<String>): List<FloatTextEmbedding> {
    val tokenIds = encoder.encodeAsIds(texts, withSpecialTokens = true, maxLen = network.maxLen)

    val batchSize = tokenIds.size
    val seqLen = tokenIds.maxOf { it.size }

    val tokensPadded = IntNDArray(batchSize, seqLen) { (batchIdx, seqIdx): IntArray -> tokenIds[batchIdx].getOrNull(seqIdx) ?: encoder.padId  }
    val attentionMaskPadded = IntNDArray(batchSize, seqLen) { (batchIdx, seqIdx): IntArray -> if (seqIdx < tokenIds[batchIdx].size) 1 else 0 }

    val embeddings: FloatNDArray = network.predict(tokensPadded, attentionMaskPadded)
    return embeddings.split(parts = embeddings.shape[0], axis = 0)
      .map { FloatTextEmbedding((it as FloatNDArray).array.toArray()) }
  }

  override suspend fun embed(texts: List<String>, model: String?): List<FloatTextEmbedding> {
    return embed(texts)
  }
}
