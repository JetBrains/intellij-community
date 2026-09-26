// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@JsonSerialize(using = JsonDateTime.Serializer::class)
@JsonDeserialize(using = JsonDateTime.Deserializer::class)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonDateTime(val instant: ZonedDateTime = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)) {
  class Serializer : ValueSerializer<JsonDateTime>() {
    override fun serialize(value: JsonDateTime, gen: JsonGenerator, serializers: SerializationContext) {
      gen.writeString(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(value.instant))
    }
  }

  class Deserializer : ValueDeserializer<JsonDateTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): JsonDateTime =
      JsonDateTime(ZonedDateTime.parse(p.valueAsString, DateTimeFormatter.ISO_ZONED_DATE_TIME))
  }

  fun presentableLocalDateTime(): String =
    instant.withZoneSameInstant(ZoneOffset.systemDefault()).format(LOCAL_DATE_TIME_SHORT_FORMAT)

  fun presentableLocalDateTimeWithMilliseconds(): String =
    instant.withZoneSameInstant(ZoneOffset.systemDefault()).format(LOCAL_DATE_TIME_FORMAT_WITH_MILLISECONDS)

  companion object {
    val LOCAL_DATE_TIME_SHORT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss dd MMM")
    val LOCAL_DATE_TIME_FORMAT_WITH_MILLISECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS dd MMM")
  }
}
