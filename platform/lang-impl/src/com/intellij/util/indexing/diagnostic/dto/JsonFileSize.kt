// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.BytesNumber

@JsonSerialize(using = JsonFileSize.Serializer::class)
@JsonDeserialize(using = JsonFileSize.Deserializer::class)
data class JsonFileSize(val bytes: BytesNumber) {
  object Serializer : JsonSerializer<JsonFileSize>() {
    override fun serialize(value: JsonFileSize, gen: JsonGenerator, serializers: SerializerProvider?) {
      gen.writeNumber(value.bytes)
    }
  }

  object Deserializer : JsonDeserializer<JsonFileSize>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): JsonFileSize = JsonFileSize(p.longValue)
  }

  fun presentableSize(): String = StringUtil.formatFileSize(bytes)
}