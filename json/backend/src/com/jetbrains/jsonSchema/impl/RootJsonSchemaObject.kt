// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.jetbrains.jsonSchema.impl.light.JsonSchemaNodePointer
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy

interface RootJsonSchemaObject<T, V> where V : JsonSchemaObject, V : JsonSchemaNodePointer<T> {
  fun getChildSchemaObjectByName(parentSchemaObject: V, vararg childNodeRelativePointer: String): JsonSchemaObject?
  fun getSchemaObjectByAbsoluteJsonPointer(jsonPointer: String): JsonSchemaObject?

  fun resolveId(id: String): String?
  fun resolveDynamicAnchor(anchor: String): String?

  val schemaInterpretationStrategy: JsonSchemaInterpretationStrategy
}