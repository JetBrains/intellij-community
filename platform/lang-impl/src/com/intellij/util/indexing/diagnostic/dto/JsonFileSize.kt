// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.diagnostic.NumberOfBytes
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize

@JsonSerialize(using = JsonFileSize.Serializer::class)
@JsonDeserialize(using = JsonFileSize.Deserializer::class)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonFileSize(val bytes: NumberOfBytes = 0) {
  object Serializer : ValueSerializer<JsonFileSize>() {
    override fun serialize(value: JsonFileSize, gen: JsonGenerator, serializers: SerializationContext) {
      gen.writeNumber(value.bytes)
    }
  }

  object Deserializer : ValueDeserializer<JsonFileSize>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): JsonFileSize = JsonFileSize(p.longValue)
  }

  fun presentableSize(): String = StringUtil.formatFileSize(bytes)
}
