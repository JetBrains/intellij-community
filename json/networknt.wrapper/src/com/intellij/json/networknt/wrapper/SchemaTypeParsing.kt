// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.json.JsonBundle
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import tools.jackson.databind.JsonNode

fun buildTypeErrorMessage(
  expectedTypes: Array<JsonSchemaType>,
  actualType: JsonSchemaType?,
  isComposition: Boolean = false,
): String {
  if (expectedTypes.isEmpty()) {
    return JsonBundle.message("schema.validation.incompatible.types")
  }

  val actualDesc = if (actualType != null) {
    " " + JsonBundle.message("schema.validation.actual") + actualType.description + "."
  }
  else ""

  val incompatibleMsg = JsonBundle.message("schema.validation.incompatible.types")
  return if (expectedTypes.size == 1 && !isComposition) {
    "$incompatibleMsg\n " + JsonBundle.message("schema.validation.required.one", expectedTypes[0].description, actualDesc)
  }
  else {
    val typeStr = expectedTypes.map { it.description }.sorted().joinToString(", ")
    "$incompatibleMsg\n " + JsonBundle.message("schema.validation.required.one.of", typeStr, actualDesc)
  }
}

fun detectActualType(instanceNode: JsonNode?): JsonSchemaType? {
  if (instanceNode == null) return null
  return when {
    instanceNode.isTextual -> JsonSchemaType._string
    instanceNode.isInt || instanceNode.isLong || instanceNode.isBigInteger -> JsonSchemaType._integer
    instanceNode.isFloat || instanceNode.isDouble || instanceNode.isBigDecimal -> JsonSchemaType._number
    instanceNode.isBoolean -> JsonSchemaType._boolean
    instanceNode.isNull -> JsonSchemaType._null
    instanceNode.isObject -> JsonSchemaType._object
    instanceNode.isArray -> JsonSchemaType._array
    else -> null
  }
}

fun parseExpectedTypes(typeString: String): Array<JsonSchemaType> {
  // Parse type string like "string" or "[string, number]"
  val cleanedString = typeString.removeSurrounding("[", "]").trim()

  return cleanedString.split(",")
    .mapNotNull { type ->
      parseSchemaType(type.trim().removeSurrounding("\""))
    }
    .toTypedArray()
}

fun parseSchemaType(typeString: String): JsonSchemaType? {
  return when (typeString) {
    "string" -> JsonSchemaType._string
    "number" -> JsonSchemaType._number
    "integer" -> JsonSchemaType._integer
    "object" -> JsonSchemaType._object
    "array" -> JsonSchemaType._array
    "boolean" -> JsonSchemaType._boolean
    "null" -> JsonSchemaType._null
    else -> null
  }
}
