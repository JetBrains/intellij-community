// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.reflect.KType

val DefaultJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

@OptIn(ExperimentalSerializationApi::class)
val LenientJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
  isLenient = true
  allowTrailingComma = true
  allowComments = true
}

fun <T> JsonElement.lenientDecodeOrNull(deserializer: DeserializationStrategy<T>): T? {
  return try {
    LenientJson.decodeFromJsonElement(deserializer, this)
  }
  catch (_: Throwable) {
    null
  }
}

fun JsonElement.lenientDecodeOrNull(type: KType, json: Json? = null): Any? {
  return try {
    (json?.let { Json(it) { isLenient = true } } ?: LenientJson)
      .decodeFromJsonElement(LenientJson.serializersModule.serializer(type), this)
  }
  catch (_: Throwable) {
    null
  }
}

