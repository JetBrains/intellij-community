// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions.v201909

import com.jetbrains.jsonSchema.extension.JsonSchemaValidation
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import com.jetbrains.jsonSchema.impl.light.*
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy
import com.jetbrains.jsonSchema.impl.validations.getSchema7AndEarlierValidations

internal data object JsonSchema201909Strategy : JsonSchemaInterpretationStrategy {
  override val idKeyword: String = JSON_DOLLAR_ID
  override val nonPositionalItemsKeyword: String = ADDITIONAL_ITEMS
  override val positionalItemsKeyword: String = ITEMS
  override val definitionsKeyword: String = DEFS
  override val dynamicReferenceKeyword: String = RECURSIVE_REF
  override val dynamicAnchorKeyword: String = RECURSIVE_ANCHOR
  override val dependencySchemasKeyword: String = DEPENDENT_SCHEMAS
  override val propertyDependenciesKeyword: String = DEPENDENT_REQUIRED
  override val unevaluatedItemsKeyword: String = UNEVALUATED_ITEMS
  override val unevaluatedPropertiesKeyword: String = UNEVALUATED_PROPERTIES
  override val referenceResolvers: Sequence<JsonSchemaReferenceResolver> =
    sequenceOf(RecursiveRefResolver, LocalSchemaReferenceResolver, RemoteSchemaReferenceResolver, Vocabulary2019Resolver)

  override fun getValidations(schemaNode: JsonSchemaObject, type: JsonSchemaType?, value: JsonValueAdapter): Sequence<JsonSchemaValidation> {
    return getSchema7AndEarlierValidations(schemaNode, type, value)
  }
}