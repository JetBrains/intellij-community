// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JsonSchemaReader2")
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.jsonSchema.impl.JsonSchemaObject

internal fun renderSchemaNode(schemaNode: JsonSchemaObject, language: JsonSchemaObjectRenderingLanguage): String {
  val mappedNode =
    when (schemaNode) {
      is JsonSchemaObjectBackedByJacksonBase -> schemaNode.rawSchemaNode
      else -> {
        Logger.getInstance("JsonSchemaReader").warn(
          "Unsupported JsonSchemaObject implementation provided: ${schemaNode::class.java.simpleName}")
        return schemaNode.toString()
      }
    }

  val mapper = when (language) {
    JsonSchemaObjectRenderingLanguage.JSON -> jsonObjectMapper
    JsonSchemaObjectRenderingLanguage.YAML -> yamlObjectMapper
  }

  return serializeJsonNodeSafe(mappedNode, mapper)
}

private fun serializeJsonNodeSafe(jsonNode: JsonNode, serializer: ObjectMapper): String {
  return runCatching {
    serializer.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode).trim(' ', '\n')
  }.getOrElse {
    Logger.getInstance("JsonSchemaReader").warn("Error during JsonSchemaObjectSerialization", it)
    ""
  }
}

internal enum class JsonSchemaObjectRenderingLanguage {
  JSON, YAML
}