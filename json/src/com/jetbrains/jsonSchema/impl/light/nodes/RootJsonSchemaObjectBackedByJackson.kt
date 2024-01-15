// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.impl.light.JSON_DOLLAR_ID
import com.jetbrains.jsonSchema.impl.light.SCHEMA_ROOT_POINTER

internal class RootJsonSchemaObjectBackedByJackson(rootNode: JsonNode, val schemaFile: VirtualFile?)
  : JsonSchemaObjectBackedByJacksonBase(rootNode, SCHEMA_ROOT_POINTER) {

  internal val schemaObjectFactory = JsonSchemaObjectBackedByJacksonFactory(this)

  override fun getFileUrl(): String? {
    return schemaFile?.url
  }

  override fun getRawFile(): VirtualFile? {
    return schemaFile
  }

  override fun getRootSchemaObject(): RootJsonSchemaObjectBackedByJackson {
    return this
  }

  override fun resolveId(id: String): String? {
    return ids[id]
  }

  private val ids by lazy {
    collectIds(rootNode).toMap()
  }

  private fun collectIds(root: JsonNode, parentPointer: List<String> = emptyList()): Sequence<Pair<String, String>> {
    return when {
      root.isObject -> {
        root.fields().asSequence().flatMap { (name, objectField) ->
          collectIds(objectField, parentPointer + name)
        }
      }
      root.isArray -> {
        root.elements().asSequence().flatMapIndexed { index, arrayItem ->
          collectIds(arrayItem, parentPointer + index.toString())
        }
      }
      root.isTextual -> {
        if (parentPointer.lastOrNull() == JSON_DOLLAR_ID)
          sequenceOf(root.asText() to parentPointer.take(parentPointer.size - 1)
            .joinToString(prefix = "/", separator = "/", transform = ::escapeForbiddenJsonPointerSymbols))
        else
          emptySequence()
      }
      else -> emptySequence()
    }
  }
}