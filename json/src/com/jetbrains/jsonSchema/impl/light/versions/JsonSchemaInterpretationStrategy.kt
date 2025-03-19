// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions

import com.jetbrains.jsonSchema.extension.JsonSchemaValidation
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.impl.MergedJsonSchemaObject
import com.jetbrains.jsonSchema.impl.light.*
import com.jetbrains.jsonSchema.impl.light.nodes.InheritedJsonSchemaObjectView
import com.jetbrains.jsonSchema.impl.light.versions.v201909.JsonSchema201909Strategy
import com.jetbrains.jsonSchema.impl.light.versions.v202012.JsonSchema202012Strategy
import org.jetbrains.annotations.ApiStatus

internal fun computeJsonSchemaVersion(schemaFieldValue: String?): JsonSchemaInterpretationStrategy {
  return when (JsonSchemaVersion.byId(schemaFieldValue.orEmpty())) {
    JsonSchemaVersion.SCHEMA_2020_12 -> JsonSchema202012Strategy
    JsonSchemaVersion.SCHEMA_2019_09 -> JsonSchema201909Strategy
    JsonSchemaVersion.SCHEMA_7 -> JsonSchema7Strategy
    else -> JsonSchema6AndEarlierStrategy
  }
}

/**
 * This interface represents a strategy for interpreting various JSON schema versions.
 *
 * Each keyword in the JSON schema is associated with a property in this interface.
 * The property returns the keyword value or null if the keyword is not supported by a particular schema version.
 *
 * Different behavior patterns might be represented by custom interfaces with several available implementations,
 * e.g., there might be several reference resolvers or node validators.
 *
 * IMPORTANT: This class is intentionally marked as internal,
 * thus it might be easily changed and adapted to the new json schema releases without breaking the public API.
 */
@ApiStatus.Internal
interface JsonSchemaInterpretationStrategy {
  val constKeyword: String? get() = CONST
  val exampleKeyword: String? get() = EXAMPLE
  val deprecationKeyword: String? get() = DEPRECATION
  val typeKeyword: String? get() = TYPE
  val multipleOfKeyword: String? get() = MULTIPLE_OF
  val maximumKeyword: String? get() = MAXIMUM
  val exclusiveMaximumKeyword: String? get() = EXCLUSIVE_MAXIMUM
  val minimumKeyword: String? get() = MINIMUM
  val exclusiveMinimumKeyword: String? get() = EXCLUSIVE_MINIMUM
  val maxLengthKeyword: String? get() = MAX_LENGTH
  val minLengthKeyword: String? get() = MIN_LENGTH
  val patternKeyword: String? get() = PATTERN
  val additionalPropertiesKeyword: String? get() = ADDITIONAL_PROPERTIES
  val propertyNamesKeyword: String? get() = PROPERTY_NAMES
  val maxItemsKeyword: String? get() = MAX_ITEMS
  val minItemsKeyword: String? get() = MIN_ITEMS
  val uniqueItemsKeyword: String? get() = UNIQUE_ITEMS
  val maxPropertiesKeyword: String? get() = MAX_PROPERTIES
  val minPropertiesKeyword: String? get() = MIN_PROPERTIES
  val requiredKeyword: String? get() = REQUIRED
  val referenceKeyword: String? get() = REF
  val defaultKeyword: String? get() = DEFAULT
  val formatKeyword: String? get() = FORMAT
  val anchorKeyword: String? get() = ANCHOR
  val descriptionKeyword: String? get() = DESCRIPTION
  val titleKeyword: String? get() = TITLE
  val enumKeyword: String? get() = ENUM
  val allOfKeyword: String? get() = ALL_OF
  val anyOfKeyword: String? get() = ANY_OF
  val oneOfKeyword: String? get() = ONE_OF
  val ifKeyword: String? get() = IF
  val thenKeyword: String? get() = THEN
  val elseKeyword: String? get() = ELSE
  val notKeyword: String? get() = NOT
  val propertiesKeyword: String? get() = JSON_PROPERTIES
  val patternPropertiesKeyword: String? get() = PATTERN_PROPERTIES
  val itemsSchemaKeyword: String? get() = ITEMS
  val containsKeyword: String? get() = CONTAINS

  val propertyDependenciesKeyword: String?
  val dependencySchemasKeyword: String?
  val idKeyword: String?
  val nonPositionalItemsKeyword: String?
  val positionalItemsKeyword: String?
  val definitionsKeyword: String?
  val dynamicReferenceKeyword: String?
  val dynamicAnchorKeyword: String?
  val unevaluatedItemsKeyword: String?
  val unevaluatedPropertiesKeyword: String?
  val referenceResolvers: Sequence<JsonSchemaReferenceResolver>

  fun getValidations(schemaNode: JsonSchemaObject, type: JsonSchemaType?, value: JsonValueAdapter): Sequence<JsonSchemaValidation>

  fun inheritBaseSchema(baseSchema: JsonSchemaObject, childSchema: JsonSchemaObject): JsonSchemaObject {
    return when {
      baseSchema == childSchema -> childSchema
      doesAlreadyInheritAnything(baseSchema) -> InheritedJsonSchemaObjectView(baseSchema, childSchema)
      isIfThenElseBranchWithNonEmptyParent(baseSchema, childSchema) -> InheritedJsonSchemaObjectView(baseSchema, childSchema)
      isApplicatorBranchWithNonEmptyParent(baseSchema, childSchema) -> InheritedJsonSchemaObjectView(baseSchema, childSchema)
      !isSameSchemaFileNodes(baseSchema, childSchema) -> InheritedJsonSchemaObjectView(baseSchema, childSchema)
      else -> childSchema
    }
  }

  fun doesAlreadyInheritAnything(jsonSchemaObject: JsonSchemaObject): Boolean {
    return jsonSchemaObject is MergedJsonSchemaObject && jsonSchemaObject.isInherited
  }

  private fun isSameSchemaFileNodes(baseSchema: JsonSchemaObject, childSchema: JsonSchemaObject): Boolean {
    return baseSchema.fileUrl == null
           || !baseSchema.fileUrl.isNullOrBlank() && baseSchema.fileUrl == childSchema.fileUrl
           || baseSchema.rawFile != null && baseSchema.rawFile == childSchema.rawFile
  }

  private fun isApplicatorBranchWithNonEmptyParent(
    baseSchema: JsonSchemaObject,
    childSchema: JsonSchemaObject,
  ): Boolean {
    if (!baseSchema.hasChildFieldsExcept(APPLICATOR_MARKERS)) return false

    return sequence {
      yieldAll(baseSchema.oneOf.orEmpty())
      yieldAll(baseSchema.anyOf.orEmpty())
      yieldAll(baseSchema.allOf.orEmpty())
    }.any { it == childSchema }
  }

  private fun isIfThenElseBranchWithNonEmptyParent(
    baseSchema: JsonSchemaObject,
    childSchema: JsonSchemaObject,
  ): Boolean {
    if (!baseSchema.hasChildFieldsExcept(IF_ELSE_MARKERS)) return false

    return baseSchema.ifThenElse?.any { condition ->
      condition.then == childSchema || condition.`else` == childSchema
    } ?: false
  }
}

private val IF_ELSE_MARKERS = listOf(IF, THEN, ELSE)
private val APPLICATOR_MARKERS = listOf(ONE_OF, ALL_OF, ANY_OF)