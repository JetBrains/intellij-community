// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.settings.mappings

import com.intellij.json.JsonBundle
import com.intellij.util.xmlb.Converter
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion

// This converter exists because a long time ago the JsonSchemaVersion class was directly used in the UI state persisting,
// and the version's identity was computed simply by calling toString() method.
// Important pitfall here is that the toString() method uses language-dependent values from the message bundle,
// so it's crucial to restore values using the same bundle messages as before :(
internal class JsonSchemaVersionConverter : Converter<JsonSchemaVersion?>() {
  override fun fromString(value: String): JsonSchemaVersion? {
    return findSuitableVersion(value)
  }

  override fun toString(value: JsonSchemaVersion?): String? {
    return value?.toString()
  }

  private fun findSuitableVersion(effectiveSerialisedValue: String): JsonSchemaVersion {
    return JsonSchemaVersion.entries
             .firstOrNull { version -> canBeSerializedInto(version, effectiveSerialisedValue) }
           ?: JsonSchemaVersion.SCHEMA_4
  }

  private fun canBeSerializedInto(version: JsonSchemaVersion, effectiveSerialisedValue: String): Boolean {
    val normalizedValue = normalizeGroupingSeparators(effectiveSerialisedValue)
    return getPossibleSerializedValues(version).any { possibleValue ->
      possibleValue == effectiveSerialisedValue || normalizeGroupingSeparators(possibleValue) == normalizedValue
    }
  }

  private fun getPossibleSerializedValues(version: JsonSchemaVersion): Sequence<String> {
    val versionSuffix = version.presentableVersionSuffix
    return sequenceOf(
      JsonBundle.message("schema.of.version", versionSuffix),
      JsonBundle.message("schema.of.version.deprecated", versionSuffix)
    )
  }

  private fun normalizeGroupingSeparators(value: String): String {
    return value.replace(GROUPING_SEPARATORS, "")
  }

  companion object {
    private val GROUPING_SEPARATORS = Regex("[\\s\\u00A0\\u202F\\u2019',.]")
  }
}
