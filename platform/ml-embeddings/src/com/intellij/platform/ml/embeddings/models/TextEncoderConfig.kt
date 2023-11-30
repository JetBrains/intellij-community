// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.models

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import ai.grazie.utils.mpp.DataLoader
import com.fasterxml.jackson.databind.DeserializationFeature

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
