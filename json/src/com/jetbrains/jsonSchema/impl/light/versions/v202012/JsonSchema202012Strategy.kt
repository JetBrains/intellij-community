// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions.v202012

import com.jetbrains.jsonSchema.extension.JsonSchemaValidation
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import com.jetbrains.jsonSchema.impl.light.*
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils
import com.jetbrains.jsonSchema.impl.light.nodes.LightweightJsonSchemaObjectMerger
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy
import com.jetbrains.jsonSchema.impl.validations.*

internal data object JsonSchema202012Strategy : JsonSchemaInterpretationStrategy {
  override val idKeyword: String = JSON_DOLLAR_ID
  override val nonPositionalItemsKeyword: String = ITEMS
  override val positionalItemsKeyword: String = PREFIX_ITEMS
  override val definitionsKeyword: String = DEFS
  override val dynamicReferenceKeyword: String = DYNAMIC_REF
  override val dynamicAnchorKeyword: String = DYNAMIC_ANCHOR
  override val dependencySchemasKeyword: String = DEPENDENT_SCHEMAS
  override val propertyDependenciesKeyword: String = DEPENDENT_REQUIRED
  override val unevaluatedItemsKeyword: String = UNEVALUATED_ITEMS
  override val unevaluatedPropertiesKeyword: String = UNEVALUATED_PROPERTIES
  override val referenceResolvers: Sequence<JsonSchemaReferenceResolver> =
    sequenceOf(LocalSchemaReferenceResolver, RemoteSchemaReferenceResolver, Vocabulary2020Resolver)

  override fun getValidations(schemaNode: JsonSchemaObject, type: JsonSchemaType?, value: JsonValueAdapter): Sequence<JsonSchemaValidation> {
    return sequence {
      if (type != null) yieldAll(getTypeValidations(type))
      yieldAll(getBaseValidations(value, schemaNode))
    }
  }

  override fun inheritBaseSchema(baseSchema: JsonSchemaObject, childSchema: JsonSchemaObject): JsonSchemaObject {
    // todo implement InheritedSchemaObject class!!!!
    // use another merged object that only uses common dynamic resolve
    if (baseSchema === childSchema || isSameSchemaFileNodes(baseSchema, childSchema)) {
      return childSchema
    }

    if (isNodeWithDifferentPointers(baseSchema, childSchema)
        || isIfThenElseBranchWithNonEmptyParent(baseSchema, childSchema)
        || isApplicatorBranchWithNonEmptyParent(baseSchema, childSchema)) {
      return LightweightJsonSchemaObjectMerger.mergeObjects(baseSchema, childSchema, baseSchema)
    }
    return childSchema
  }

  private fun isSameSchemaFileNodes(baseSchema: JsonSchemaObject, childSchema: JsonSchemaObject): Boolean {
    return baseSchema.fileUrl == null
           || !baseSchema.fileUrl.isNullOrBlank() && baseSchema.fileUrl == childSchema.fileUrl
           || baseSchema.rawFile != null && baseSchema.rawFile == childSchema.rawFile
  }

  private fun isNodeWithDifferentPointers(baseSchema: JsonSchemaObject,
                                         childSchema: JsonSchemaObject): Boolean {
    return baseSchema.pointer != childSchema.pointer
  }

  private fun isApplicatorBranchWithNonEmptyParent(baseSchema: JsonSchemaObject,
                                                   childSchema: JsonSchemaObject): Boolean {
    if (!baseSchema.hasChildFieldsExcept(ONE_OF, ALL_OF, ANY_OF)) return false

    return sequence {
      yieldAll(baseSchema.oneOf.orEmpty())
      yieldAll(baseSchema.anyOf.orEmpty())
      yieldAll(baseSchema.allOf.orEmpty())
    }.any { it == childSchema }
  }

  private fun isIfThenElseBranchWithNonEmptyParent(baseSchema: JsonSchemaObject,
                                                   childSchema: JsonSchemaObject): Boolean {
    if (!baseSchema.hasChildFieldsExcept(IF, THEN, ELSE)) return false

    return baseSchema.ifThenElse?.any { condition ->
      condition.then == childSchema || condition.`else` == childSchema
    } ?: false
  }

  private fun getBaseValidations(value: JsonValueAdapter, schemaNode: JsonSchemaObject): Sequence<JsonSchemaValidation> {
    if (schemaNode.constantSchema != null) {
      return sequenceOf(ConstantSchemaValidation)
    }

    return sequence {
      yield(EnumValidation.INSTANCE)

      if (!value.isShouldBeIgnored) {
        if (JsonSchemaObjectReadingUtils.hasNumericChecks(schemaNode) && value.isNumberLiteral) {
          yield(NumericValidation.INSTANCE)
        }
        if (JsonSchemaObjectReadingUtils.hasStringChecks(schemaNode) && value.isStringLiteral) {
          yield(StringValidation.INSTANCE)
        }
        if (JsonSchemaObjectReadingUtils.hasArrayChecks(schemaNode) && value.isArray) {
          yield(Array2020Validator)
        }
        if (hasMinMaxLengthChecks(schemaNode)) {
          if (value.isStringLiteral) {
            yield(StringValidation.INSTANCE)
          }
          else if (value.isArray) {
            yield(Array2020Validator)
          }
        }
        if (JsonSchemaObjectReadingUtils.hasObjectChecks(schemaNode) && value.isObject) {
          yield(ObjectValidation.INSTANCE)
        }
      }

      if (schemaNode.not != null) {
        yield(NotValidation.INSTANCE)
      }
    }
  }

  private fun getTypeValidations(type: JsonSchemaType): Sequence<JsonSchemaValidation> {
    return sequence {
      yield(TypeValidation.INSTANCE)
      when (type) {
        JsonSchemaType._string_number -> {
          yield(NumericValidation.INSTANCE)
          yield(StringValidation.INSTANCE)
        }
        JsonSchemaType._number, JsonSchemaType._integer -> {
          yield(NumericValidation.INSTANCE)
        }
        JsonSchemaType._string -> {
          yield(StringValidation.INSTANCE)
        }
        JsonSchemaType._array -> {
          yield(Array2020Validator)
        }
        JsonSchemaType._object -> {
          yield(ObjectValidation.INSTANCE)
        }
        else -> {}
      }
    }
  }
}