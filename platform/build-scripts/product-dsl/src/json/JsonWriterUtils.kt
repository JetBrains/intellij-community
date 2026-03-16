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