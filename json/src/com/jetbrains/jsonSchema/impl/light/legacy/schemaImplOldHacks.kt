// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.legacy

import com.jetbrains.jsonSchema.impl.light.DESCRIPTION
import com.jetbrains.jsonSchema.impl.light.X_INTELLIJ_ENUM_METADATA
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase

private val fieldNamesOldParserIsAwareOf =
  setOf("\$anchor", "\$id", "id", "\$schema", "description", "deprecationMessage", "x-intellij-html-description",
        "x-intellij-language-injection", "x-intellij-case-insensitive", "x-intellij-enum-metadata", "title", "\$ref",
        "\$recursiveRef", "\$recursiveAnchor", "default", "example", "format", "definitions", "\$defs", "properties",
        "items", "multipleOf", "maximum", "minimum", "exclusiveMaximum", "exclusiveMinimum", "maxLength", "minLength",
        "pattern", "additionalItems", "contains", "maxItems", "minItems", "uniqueItems", "maxProperties", "minProperties",
        "required", "additionalProperties", "propertyNames", "patternProperties", "dependencies", "enum",
        "const", "type", "allOf", "anyOf", "oneOf", "not", "if", "then", "else", "instanceof", "typeof")

internal fun isOldParserAwareOfFieldName(fieldName: String): Boolean {
  return fieldName in fieldNamesOldParserIsAwareOf
}

// Old code did not have any tests for this method, so the updated implementation might have mistakes.
// Consider adding a test if you know something about 'x-intellij-enum-metadata'
internal fun JsonSchemaObjectBackedByJacksonBase.tryReadEnumMetadata(): Map<String, Map<String, String>>? {
  return rawSchemaNode.fields()?.asSequence()?.singleOrNull {
    it.key == X_INTELLIJ_ENUM_METADATA
  }?.value?.fields()?.asSequence()
    ?.mapNotNull { (name, valueNode) ->
      when {
        valueNode.isTextual -> {
          name to mapOf(DESCRIPTION to valueNode.asText())
        }
        valueNode.isObject -> {
          name to valueNode.fields().asSequence()
            .mapNotNull { (fieldName, fieldValue) ->
              if (fieldValue.isTextual) {
                fieldName to fieldValue.asText()
              }
              else {
                null
              }
            }.toMap()
        }
        else -> null
      }
    }?.toMap()
}