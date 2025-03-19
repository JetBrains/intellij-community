// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.sequenceOfNotNull
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.RootJsonSchemaObject
import com.jetbrains.jsonSchema.impl.light.SCHEMA_ROOT_POINTER
import com.jetbrains.jsonSchema.impl.light.X_INTELLIJ_LANGUAGE_INJECTION
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy
import com.jetbrains.jsonSchema.impl.light.versions.computeJsonSchemaVersion
import org.jetbrains.annotations.ApiStatus

private val IDS_MAP_KEY = Key<Map<String, String>>("ids")
private val DYNAMIC_ANCHORS_MAP_KEY = Key<Map<String, String>>("dynamicAnchors")
private val INJECTIONS_MAP_KEY = Key<Boolean>("injections")
private val DEPRECATIONS_MAP_KEY = Key<Boolean>("deprecations")
private val FILE_URL_MAP_KEY = Key<String>("fileUrl")

@ApiStatus.Internal
class RootJsonSchemaObjectBackedByJackson(rootNode: JsonNode, val schemaFile: VirtualFile?)
  : JsonSchemaObjectBackedByJacksonBase(rootNode, SCHEMA_ROOT_POINTER), RootJsonSchemaObject<JsonNode, JsonSchemaObjectBackedByJacksonBase> {

  private val schemaObjectFactory = JsonSchemaObjectBackedByJacksonFactory(this)
  override val schemaInterpretationStrategy: JsonSchemaInterpretationStrategy = computeJsonSchemaVersion(schema)

  override fun getChildSchemaObjectByName(parentSchemaObject: JsonSchemaObjectBackedByJacksonBase, vararg childNodeRelativePointer: String): JsonSchemaObjectBackedByJacksonBase? {
    return schemaObjectFactory.getChildSchemaObjectByName(parentSchemaObject, *childNodeRelativePointer)
  }

  override fun getSchemaObjectByAbsoluteJsonPointer(jsonPointer: String): JsonSchemaObject? {
    return schemaObjectFactory.getSchemaObjectByAbsoluteJsonPointer(jsonPointer)
  }

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

  fun checkHasDeprecations(): Boolean {
    val deprecationMarker = schemaInterpretationStrategy.deprecationKeyword ?: return false
    return getOrComputeValue(DEPRECATIONS_MAP_KEY) {
      indexSchema(rawSchemaNode) { _, parentPointer ->
        if (parentPointer.lastOrNull() == deprecationMarker)
          true
        else
          null
      }.any { it }
    }
  }

  override fun getFileUrl(): String? {
    return getOrComputeValue(FILE_URL_MAP_KEY) {
      schemaFile?.url.orEmpty()
    }.takeIf { it.isNotEmpty() }
  }

  override fun getRawFile(): VirtualFile? {
    return schemaFile
  }

  override fun getRootSchemaObject(): RootJsonSchemaObjectBackedByJackson {
    return this
  }

  override fun resolveId(id: String): String? {
    val schemaFeature = schemaInterpretationStrategy.idKeyword ?: return null
    return collectValuesWithKey(schemaFeature, IDS_MAP_KEY)[id]
  }

  override fun resolveDynamicAnchor(anchor: String): String? {
    val schemaFeature = schemaInterpretationStrategy.dynamicAnchorKeyword ?: return null
    return collectValuesWithKey(schemaFeature, DYNAMIC_ANCHORS_MAP_KEY)[anchor]
  }

  private fun collectValuesWithKey(expectedKey: String, storeIn: Key<Map<String, String>>): Map<String, String> {
    return getOrComputeValue(storeIn) {
      indexSchema(rawSchemaNode) { node, parentPointer ->
        if (!node.isTextual || parentPointer.lastOrNull() != expectedKey) return@indexSchema null

        val leafNodeText = node.asText()
        val jsonPointer = parentPointer.take(parentPointer.size - 1)
          .joinToString(prefix = "/", separator = "/", transform = ::escapeForbiddenJsonPointerSymbols)

        leafNodeText to jsonPointer
      }.toMap()
    }
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