// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.legacy

import com.intellij.openapi.util.registry.Registry
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.light.nodes.LightweightJsonSchemaObjectMerger

@Deprecated("Intermediate abstraction needed only until JsonSchemaObjectImpl is not deleted")
interface JsonSchemaObjectMerger {
  fun mergeObjects(base: JsonSchemaObject, other: JsonSchemaObject, pointTo: JsonSchemaObject): JsonSchemaObject
}

fun getJsonSchemaObjectMerger(): JsonSchemaObjectMerger {
  return if (Registry.`is`("json.schema.object.v2"))
    LightweightJsonSchemaObjectMerger
  else
    LegacyMutableJsonSchemaObjectMerger()
}