// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.util.asSafely
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase

// todo: avoid casting after JsonSchemaObject is made an interface
// todo: move merging logic here or this function to the merger
internal fun inheritBaseSchemaIfNeeded(parent: JsonSchemaObject, child: JsonSchemaObject): JsonSchemaObject {
  val parentUrl = parent.fileUrl
  val childUrl = child.fileUrl
  if (parentUrl == null || parentUrl == childUrl) return child

  return child.asSafely<JsonSchemaObjectBackedByJacksonBase>()
           ?.rootSchemaObject
           ?.schemaInterpretationStrategy
           ?.inheritBaseSchema(parent, child)
         ?: child
}