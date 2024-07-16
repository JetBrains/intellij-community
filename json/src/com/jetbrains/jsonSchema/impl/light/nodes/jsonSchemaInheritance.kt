// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.intellij.util.asSafely
import com.jetbrains.jsonSchema.impl.JsonSchemaObject

// todo: avoid casting after JsonSchemaObject is made an interface
internal fun inheritBaseSchemaIfNeeded(parent: JsonSchemaObject, child: JsonSchemaObject): JsonSchemaObject {
  //val parentUrl = parent.fileUrl
  //val childUrl = child.fileUrl
  //if (parentUrl == null || parentUrl == childUrl) return child

  return child.asSafely<JsonSchemaObjectBackedByJacksonBase>()
           ?.rootSchemaObject
           ?.schemaInterpretationStrategy
           ?.inheritBaseSchema(parent, child)
         ?: child

}