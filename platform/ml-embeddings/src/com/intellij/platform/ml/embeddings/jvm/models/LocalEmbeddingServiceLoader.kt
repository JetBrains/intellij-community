// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.models

import ai.grazie.nlp.encoder.PreTrainedTextEncoder
import ai.grazie.nlp.encoder.bert.uncased.BertBaseUncasedTextEncoder
import ai.grazie.utils.mpp.*
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.EOFException

class LocalEmbeddingServiceLoader {
  suspend fun load(loader: RootDataLoader): LocalEmbeddingService? {
    try {
      val network = loadNetwork(loader)
      val encoder = loadTextEncoder(loader)
      return LocalEmbeddingService(network, encoder)
    } catch (_: EOFException) {
      return null
    }
  }

  private suspend fun loadNetwork(loader: RootDataLoader): LocalEmbeddingNetwork {
    val modelLoader = PrefixedDataLoader(DataLoader.Path(MODEL_NAME, "optimized"), loader)
    val tokenizerConfigLoader = PrefixedDataLoader(DataLoader.Path(MODEL_NAME, "encoder"), loader)
    val textEncoderConfig = TextEncoderConfig.loadConfig(tokenizerConfigLoader)
    return LocalEmbeddingNetwork(modelLoader.bytes(MODEL_FILENAME), textEncoderConfig.maxLen)
  }

  private suspend fun loadTextEncoder(loader: RootDataLoader): PreTrainedTextEncoder {
    return BertBaseUncasedTextEncoder.load(loader)
  }

  companion object {
    const val MODEL_NAME: String = "dan-bert-tiny"
    const val MODEL_FILENAME: String = "dan_optimized_fp16.onnx"
  }
}

data class TextEncoderConfig(
  @JsonSetter("model_max_length")
  val maxLen: Int?,
) {
  companion object {
    private val objectMapper = jacksonObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    suspend fun loadConfig(loader: DataLoader): TextEncoderConfig {
      return objectMapper.readValue(loader.text("tokenizer_config.json"))
    }
  }
}