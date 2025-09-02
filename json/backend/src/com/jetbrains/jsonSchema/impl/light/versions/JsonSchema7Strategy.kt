// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions

import com.jetbrains.jsonSchema.extension.JsonSchemaValidation
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import com.jetbrains.jsonSchema.impl.light.*
import com.jetbrains.jsonSchema.impl.validations.getSchema7AndEarlierValidations

internal data object JsonSchema7Strategy : JsonSchemaInterpretationStrategy {
  override val idKeyword: String = JSON_DOLLAR_ID
  override val nonPositionalItemsKeyword: String = ADDITIONAL_ITEMS
  override val positionalItemsKeyword: String = ITEMS
  override val definitionsKeyword: String = JSON_DEFINITIONS
  override val dynamicReferenceKeyword: String? = null
  override val dynamicAnchorKeyword: String? = null
  override val dependencySchemasKeyword: String = DEPENDENCIES
  override val propertyDependenciesKeyword: String = DEPENDENCIES
  override val unevaluatedItemsKeyword: String? = null
  override val unevaluatedPropertiesKeyword: String? = null

  override val referenceResolvers: Sequence<JsonSchemaReferenceResolver> =
    sequenceOf(LocalSchemaReferenceResolver, RemoteSchemaReferenceResolver)

  override fun getValidations(schemaNode: JsonSchemaObject, type: JsonSchemaType?, value: JsonValueAdapter): Sequence<JsonSchemaValidation> {
    return getSchema7AndEarlierValidations(schemaNode, type, value)
  }
}