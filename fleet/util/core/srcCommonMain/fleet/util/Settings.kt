// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.serialization.LenientJson
import kotlinx.serialization.json.JsonObject

fun parseJsonSettings(text: String): Map<String, String> {
  return parseJsonSettingsToJsonElement(text).mapValues { it.value.toString() }
}

fun parseJsonSettingsToJsonElement(text: String): JsonObject {
  val stripped = text.replace('\u3000', ' ')
  val jsonElement = LenientJson.parseToJsonElement(stripped)
  return jsonElement as? JsonObject ?: JsonObject(emptyMap())
}