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
import com.intellij.util.indexing.diagnostic.TimeNano
import java.util.concurrent.TimeUnit

@JsonSerialize(using = JsonDuration.Serializer::class)
@JsonDeserialize(using = JsonDuration.Deserializer::class)
data class JsonDuration(val nano: TimeNano) {
  object Serializer : JsonSerializer<JsonDuration>() {
    override fun serialize(value: JsonDuration, gen: JsonGenerator, serializers: SerializerProvider?) {
      gen.writeNumber(value.nano)
    }
  }

  object Deserializer : JsonDeserializer<JsonDuration>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): JsonDuration = JsonDuration(p.longValue)
  }

  fun presentableDuration(): String = when {
    nano == 0L -> "0"
    nano < TimeUnit.MILLISECONDS.toNanos(1) -> "< 1 ms"
    else -> StringUtil.formatDuration(nano.toMillis())
  }
}