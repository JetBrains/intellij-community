// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.serialization.LenientJson
import kotlinx.serialization.json.JsonObject

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.util.test"])
fun String.removeJsonComments(): String {
  return lineSequence().map { line ->
    val commentIndex = lineCommentIndex(line)
    if (commentIndex >= 0) {
      line.substring(0, commentIndex)
    }
    else {
      line
    }
  }.joinToString(separator = "\n")
}

private fun lineCommentIndex(line: String): Int {
  var i = 0
  var quote: Char? = null
  while (i < line.length) {
    val c = line[i]
    if (quote != null) {
      when (c) {
        '\\' -> {
          i++
        }
        quote -> {
          quote = null
        }
      }
    }
    else {
      when (c) {
        '\'' -> quote = c
        '"' -> quote = c
        '/' -> {
          if (i + 1 < line.length && line[i + 1] == '/') {
            return i
          }
        }
      }
    }
    i++
  }
  return -1
}

fun parseJsonSettings(text: String): Map<String, String> {
  return parseJsonSettingsToJsonElement(text).mapValues { it.value.toString() }
}

fun parseJsonSettingsToJsonElement(text: String): JsonObject {
  val stripped = text.removeJsonComments().replace('\u3000', ' ')
  val jsonElement = LenientJson.parseToJsonElement(stripped)
  return if (jsonElement is JsonObject) {
    jsonElement
  }
  else {
    JsonObject(emptyMap())
  }
}