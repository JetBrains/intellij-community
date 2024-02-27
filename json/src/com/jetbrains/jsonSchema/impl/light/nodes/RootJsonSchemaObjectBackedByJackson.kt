// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.sequenceOfNotNull
import com.jetbrains.jsonSchema.impl.light.JSON_DOLLAR_ID
import com.jetbrains.jsonSchema.impl.light.SCHEMA_ROOT_POINTER
import com.jetbrains.jsonSchema.impl.light.X_INTELLIJ_LANGUAGE_INJECTION

private val IDS_MAP_KEY = Key<Map<String, String>>("ids")
private val INJECTIONS_MAP_KEY = Key<Boolean>("injections")

internal class RootJsonSchemaObjectBackedByJackson(rootNode: JsonNode, val schemaFile: VirtualFile?)
  : JsonSchemaObjectBackedByJacksonBase(rootNode, SCHEMA_ROOT_POINTER) {

  internal val schemaObjectFactory = JsonSchemaObjectBackedByJacksonFactory(this)

  fun checkHasInjections(): Boolean {
    return getOrComputeValue(INJECTIONS_MAP_KEY) {
      indexSchema(rawSchemaNode) { _, parentPointer ->
        if (parentPointer.lastOrNull() == X_INTELLIJ_LANGUAGE_INJECTION)
          true
        else
          null
      }.any { it }
    }
  }

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
    return getOrComputeValue(IDS_MAP_KEY) {
      indexSchema(rawSchemaNode) { node, parentPointer ->
        if (!node.isTextual) return@indexSchema null

        if (parentPointer.lastOrNull() == JSON_DOLLAR_ID) {
          val leafNodeText = node.asText()
          val jsonPointer = parentPointer.take(parentPointer.size - 1)
            .joinToString(prefix = "/", separator = "/", transform = ::escapeForbiddenJsonPointerSymbols)

          leafNodeText to jsonPointer
        }
        else
          null
      }.toMap()
    }[id]
  }

  private fun <T : Any> indexSchema(root: JsonNode, parentPointer: List<String> = emptyList(), retrieveDataFromNode: (JsonNode, List<String>) -> T?): Sequence<T> {
    return when {
      root.isObject -> {
        root.fields().asSequence().flatMap { (name, objectField) ->
          val retrievedValue = retrieveDataFromNode(root, parentPointer)
          if (retrievedValue != null) return@flatMap sequenceOf(retrievedValue)

          indexSchema(objectField, parentPointer + name, retrieveDataFromNode)
        }
      }
      root.isArray -> {
        val retrievedValue = retrieveDataFromNode(root, parentPointer)
        if (retrievedValue != null) return sequenceOf(retrievedValue)

        root.elements().asSequence().flatMapIndexed { index, arrayItem ->
          indexSchema(arrayItem, parentPointer + index.toString(), retrieveDataFromNode)
        }
      }
      root.isTextual -> {
        val retrievedValue = retrieveDataFromNode(root, parentPointer)
        sequenceOfNotNull(retrievedValue)
      }
      else -> emptySequence()
    }
  }
}