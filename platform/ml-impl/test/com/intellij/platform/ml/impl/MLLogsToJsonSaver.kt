// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import java.io.BufferedWriter
import java.io.FileWriter
import java.nio.file.Path
import kotlin.io.path.div

class MLLogsToJsonSaver(private val path: Path) {
  private fun mapToJson(map: Map<String, Any>): String {
    val entrySet = map.entries.joinToString(", ") {
      "\"${it.key}\": ${valueToJson(it.value)}"
    }
    return "{$entrySet}"
  }

  private fun listToJson(list: List<Any>): String =
    list.joinToString(", ") { valueToJson(it) }

  @Suppress("UNCHECKED_CAST")
  private fun valueToJson(value: Any): String =
    when (value) {
      is String -> "\"$value\""
      is Map<*, *> -> mapToJson(value as Map<String, Any>)
      is List<*> -> "[${listToJson(value as List<Any>)}]"
      else -> value.toString()
    }

  fun save(list: MutableList<Pair<String, Map<String, Any>>>, name: String) {
    val logs = list.joinToString(",\n") { mapToJson(mapOf("eventId" to it.first, "data" to it.second)) }
    val content = "let logs = [\n$logs\n];"
    val filePath = path / "$name.js"
    BufferedWriter(FileWriter(filePath.toFile())).use { it.write(content) }
  }
}