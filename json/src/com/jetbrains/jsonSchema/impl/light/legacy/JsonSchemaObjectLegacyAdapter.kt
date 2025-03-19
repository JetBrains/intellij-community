// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.legacy

import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaType

@Deprecated("Will be removed along with JsonSchemaObjectImpl")
abstract class JsonSchemaObjectLegacyAdapter : JsonSchemaObject() {
  override fun mergeTypes(selfType: JsonSchemaType?,
                          otherType: JsonSchemaType?,
                          otherTypeVariants: MutableSet<JsonSchemaType>?): JsonSchemaType? {
    throw UnsupportedOperationException("Do not use!")
  }

  override fun mergeTypeVariantSets(self: MutableSet<JsonSchemaType>?, other: MutableSet<JsonSchemaType>?): MutableSet<JsonSchemaType> {
    throw UnsupportedOperationException("Do not use!")
  }

  override fun mergeValues(other: JsonSchemaObject) {
    throw UnsupportedOperationException("Do not use!")
  }

  override fun getBackReference(): JsonSchemaObject? {
    throw UnsupportedOperationException("Do not use!")
  }

  override fun getDefinitionsMap(): Map<String, JsonSchemaObject>? {
    throw UnsupportedOperationException("Use getDefinitionByName()")
  }

  override fun getProperties(): Map<String, JsonSchemaObject?> {
    throw UnsupportedOperationException("Use getPropertyByName()")
  }

  override fun getExample(): Map<String?, Any?>? {
    throw UnsupportedOperationException("Use getExampleByName()")
  }
}