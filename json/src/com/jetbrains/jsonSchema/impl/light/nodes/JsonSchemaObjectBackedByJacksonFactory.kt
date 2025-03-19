// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.util.containers.ConcurrentFactoryMap
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaReader
import com.jetbrains.jsonSchema.impl.light.JsonSchemaObjectFactory
import com.jetbrains.jsonSchema.impl.light.ROOT_POINTER_VARIANTS

internal class JsonSchemaObjectBackedByJacksonFactory(private val rootSchemaObject: RootJsonSchemaObjectBackedByJackson) : JsonSchemaObjectFactory<JsonNode, JsonSchemaObjectBackedByJacksonBase> {

  private val registeredChildren = ConcurrentFactoryMap.createMap(::computeSchemaObjectByPointer)

  override fun getSchemaObjectByAbsoluteJsonPointer(jsonPointer: String): JsonSchemaObject? {
    return registeredChildren[jsonPointer].takeIf { it !is MissingJsonSchemaObject }
  }

  override fun getChildSchemaObjectByName(parentSchemaObject: JsonSchemaObjectBackedByJacksonBase,
                                          vararg childNodeRelativePointer: String): JsonSchemaObjectBackedByJacksonBase? {
    if (childNodeRelativePointer.isEmpty()) return parentSchemaObject
    val childAbsolutePointer = computeAbsoluteJsonPointer(parentSchemaObject.pointer, *childNodeRelativePointer)

    return if (isRootObjectPointer(childAbsolutePointer))
      rootSchemaObject
    else
      registeredChildren[childAbsolutePointer].takeIf { it !is MissingJsonSchemaObject }
  }

  private fun computeSchemaObjectByPointer(jsonPointer: String): JsonSchemaObjectBackedByJacksonBase {
    val resolvedRelativeChildSchemaNode = JacksonSchemaNodeAccessor.resolveNode(rootSchemaObject.rawSchemaNode, jsonPointer)
    return if (resolvedRelativeChildSchemaNode == null || resolvedRelativeChildSchemaNode.isMissingNode || resolvedRelativeChildSchemaNode.isArray)
      MissingJsonSchemaObject
    else
      JsonSchemaObjectBackedByJackson(rootSchemaObject, resolvedRelativeChildSchemaNode, jsonPointer)
  }

  private fun computeAbsoluteJsonPointer(basePointer: String, vararg relativePointer: String): String {
    return relativePointer.asSequence()
      .map(::escapeForbiddenJsonPointerSymbols)
      .joinToString(separator = "/")
      .let { joinedPointer ->
        JsonSchemaReader.getNewPointer(joinedPointer, basePointer)
      }
  }

  private fun isRootObjectPointer(jsonPointer: String): Boolean {
    return jsonPointer in ROOT_POINTER_VARIANTS
  }
}