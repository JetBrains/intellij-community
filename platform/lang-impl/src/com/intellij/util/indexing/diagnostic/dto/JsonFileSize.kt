// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.NumberOfBytes

@JsonSerialize(using = JsonFileSize.Serializer::class)
@JsonDeserialize(using = JsonFileSize.Deserializer::class)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonFileSize(val bytes: NumberOfBytes = 0) {
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