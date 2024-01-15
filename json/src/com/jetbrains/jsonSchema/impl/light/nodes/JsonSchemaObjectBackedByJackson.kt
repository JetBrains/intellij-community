// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.databind.JsonNode

internal class JsonSchemaObjectBackedByJackson(private val rootObject: RootJsonSchemaObjectBackedByJackson,
                                               rawSchemaNode: JsonNode,
                                               jsonPointer: String) : JsonSchemaObjectBackedByJacksonBase(rawSchemaNode, jsonPointer) {
  override fun getRootSchemaObject(): RootJsonSchemaObjectBackedByJackson {
    return rootObject
  }
}