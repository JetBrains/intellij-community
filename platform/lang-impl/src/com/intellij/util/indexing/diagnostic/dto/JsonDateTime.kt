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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@JsonSerialize(using = JsonDateTime.Serializer::class)
@JsonDeserialize(using = JsonDateTime.Deserializer::class)
data class JsonDateTime(val instant: ZonedDateTime) {
  class Serializer : JsonSerializer<JsonDateTime>() {
    override fun serialize(value: JsonDateTime, gen: JsonGenerator, serializers: SerializerProvider?) {
      gen.writeString(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(value.instant))
    }
  }

  class Deserializer : JsonDeserializer<JsonDateTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): JsonDateTime =
      JsonDateTime(ZonedDateTime.parse(p.valueAsString, DateTimeFormatter.ISO_ZONED_DATE_TIME))
  }

  fun presentableDateTime(): String = instant.format(DateTimeFormatter.RFC_1123_DATE_TIME)
}