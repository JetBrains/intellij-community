// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.jetbrains.jsonSchema.impl.JsonValidationError.MissingMultiplePropsIssueData
import com.jetbrains.jsonSchema.impl.fixes.AddMissingPropertyFix
import tools.jackson.databind.JsonNode

private const val MAX_FORMAT_DEPTH = 16

fun findDuplicateIndices(arrayNode: JsonNode): IntArray {
  // JsonNode has value-based equals/hashCode, so keying by the node directly avoids the
  // quadratic per-element toString() serialization and handles object property ordering.
  val seen = mutableMapOf<JsonNode, Int>()
  val duplicates = mutableListOf<Int>()

  for (i in 0 until arrayNode.size()) {
    val prev = seen.put(arrayNode.get(i), i)
    if (prev != null) {
      if (!duplicates.contains(prev)) {
        duplicates.add(prev)
      }
      duplicates.add(i)
    }
  }

  return duplicates.toIntArray()
}

fun formatEnumValues(enumNode: JsonNode?): String {
  if (enumNode == null || !enumNode.isArray) return ""
  return (0 until enumNode.size()).joinToString(", ") { formatJsonValue(enumNode.get(it)) }
}

fun formatJsonValue(node: JsonNode, depth: Int = 0): String {
  // Cap recursion so a cyclic or maliciously deep schema constant cannot blow the stack.
  if (depth > MAX_FORMAT_DEPTH) return "…"
  return when {
    node.isTextual -> "\"${node.asText()}\""
    node.isNumber || node.isBoolean || node.isNull -> node.asText()
    node.isObject -> {
      val entries = node.properties().map { (key, value) ->
        "\"$key\": ${formatJsonValue(value, depth + 1)}"
      }
      "{${entries.joinToString(", ")}}"
    }
    node.isArray -> {
      val elements = (0 until node.size()).map { formatJsonValue(node.get(it), depth + 1) }
      "[${elements.joinToString(", ")}]"
    }
    else -> node.toString()
  }
}

/**
 * Extracts the default value and enum count from a property schema node.
 *
 * Returns a pair of (defaultValue, enumCount) where:
 * - `defaultValue` is a JVM primitive (String/Boolean/Number) or null. Using a primitive
 *   ensures [AddMissingPropertyFix.formatDefaultValue] can handle it — the
 *   `tools.jackson.databind.JsonNode` type from the networknt fork is not recognized by
 *   that method's `instanceof` check for `com.fasterxml.jackson.databind.JsonNode`.
 * - `enumCount` reflects the enum cardinality. When `enumCount == 1`,
 *   [MissingMultiplePropsIssueData.getPropertyNameWithComment] will append `= <defaultValue>`
 *   to the property name and will NPE if `defaultValue` is null. So: when the schema has
 *   exactly one enum item, we use that item as the default value; otherwise we use the
 *   explicit `"default"` field (if any).
 */
fun extractDefaultAndEnumCount(propertySchema: JsonNode?): Pair<Any?, Int> {
  if (propertySchema == null) return null to 0

  val enumNode = propertySchema.get("enum")
  val enumCount = enumNode?.size() ?: 0

  // When exactly one enum value exists, that value is implicitly the default (the old IJ
  // validator's getDefaultValueFromEnum() uses it the same way).
  val defaultValue = if (enumCount == 1 && enumNode != null) {
    extractPrimitive(enumNode.get(0))
  }
  else {
    extractPrimitive(propertySchema.get("default"))
  }

  return defaultValue to enumCount
}

fun extractPrimitive(node: JsonNode?): Any? {
  if (node == null) return null
  return when {
    node.isTextual -> node.asText()
    node.isBoolean -> node.booleanValue()
    node.isIntegralNumber -> node.longValue()
    node.isFloatingPointNumber -> node.doubleValue()
    else -> null
  }
}
