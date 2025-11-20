// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.fasterxml.jackson.core.JsonGenerator

/**
 * Helper utilities for common JSON writing patterns.
 * Reduces boilerplate in JSON writer functions.
 */

/**
 * Writes an array of strings to JSON.
 *
 * @param fieldName Name of the array field
 * @param items Collection of strings to write
 */
internal fun JsonGenerator.writeStringArray(fieldName: String, items: Collection<String>) {
  writeArrayFieldStart(fieldName)
  for (item in items) {
    writeString(item)
  }
  writeEndArray()
}

/**
 * Writes a map with mixed value types to JSON.
 * Handles String, Number, Boolean, and List<*> values.
 *
 * @param fieldName Name of the object field
 * @param map Map with mixed value types
 */
internal fun JsonGenerator.writeMapField(fieldName: String, map: Map<String, Any>) {
  writeObjectFieldStart(fieldName)
  for ((key, value) in map) {
    when (value) {
      is Number -> writeNumberField(key, value.toDouble())
      is String -> writeStringField(key, value)
      is Boolean -> writeBooleanField(key, value)
      is List<*> -> {
        writeArrayFieldStart(key)
        for (item in value) {
          writeString(item.toString())
        }
        writeEndArray()
      }
    }
  }
  writeEndObject()
}

/**
 * Writes an array of objects to JSON, using a custom writer function for each element.
 *
 * @param fieldName Name of the array field
 * @param items List of items to write
 * @param elementWriter Function to write each element
 */
internal inline fun <T> JsonGenerator.writeObjectArray(
  fieldName: String,
  items: List<T>,
  elementWriter: JsonGenerator.(T) -> Unit
) {
  writeArrayFieldStart(fieldName)
  for (item in items) {
    elementWriter(item)
  }
  writeEndArray()
}
