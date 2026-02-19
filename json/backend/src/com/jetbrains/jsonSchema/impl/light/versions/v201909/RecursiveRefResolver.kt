// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions.v201909

import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.light.JsonSchemaReferenceResolver
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase

internal data object RecursiveRefResolver : JsonSchemaReferenceResolver {
  override fun resolve(reference: String, referenceOwner: JsonSchemaObjectBackedByJacksonBase, service: JsonSchemaService): JsonSchemaObject? {
    return if (reference == "#") referenceOwner.rootSchemaObject else null
  }
}