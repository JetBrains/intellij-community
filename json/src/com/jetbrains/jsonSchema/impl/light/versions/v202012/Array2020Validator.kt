// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions.v202012

import com.intellij.json.JsonBundle
import com.jetbrains.jsonSchema.extension.JsonErrorPriority
import com.jetbrains.jsonSchema.extension.JsonValidationHost
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import com.jetbrains.jsonSchema.impl.validations.ArrayValidation
import org.jetbrains.annotations.Nls

internal object Array2020Validator : ArrayValidation() {
  override fun validate(valueAdapter: JsonValueAdapter, schema: JsonSchemaObject, schemaType: JsonSchemaType?, consumer: JsonValidationHost, options: JsonComplianceCheckerOptions) {
    val arrayItems = valueAdapter.getAsArray()?.elements ?: return

    validateUniqueItems(valueAdapter, arrayItems, schema, consumer)
    validateAgainstContainsSchema(valueAdapter, arrayItems, schema, consumer, options)
    validateIndividualItems(arrayItems, schema, consumer)
    validateArrayLength(valueAdapter, arrayItems, schema, consumer)
    validateArrayLengthHeuristically(valueAdapter, arrayItems, schema, consumer)
  }

  private fun validateIndividualItems(instanceArrayItems: List<JsonValueAdapter>, schema: JsonSchemaObject, consumer: JsonValidationHost) {
    val additionalItemsSchemaList = schema.itemsSchemaList
    val firstRegularItemIndex = if (additionalItemsSchemaList.isNullOrEmpty()) 0 else additionalItemsSchemaList.size

    // check instance items with positional schema
    for (index in 0 until firstRegularItemIndex) {
      val positionalSchema = additionalItemsSchemaList?.get(index) ?: break
      val inspectedInstanceItem = instanceArrayItems.getOrNull(index) ?: break
      consumer.checkObjectBySchemaRecordErrors(positionalSchema, inspectedInstanceItem)
    }

    // check the rest of instance items with regular schema
    val additionalItemsSchema = schema.additionalItemsSchema
    if (additionalItemsSchema != null) {
      validateAgainstNonPositionalSchema(additionalItemsSchema, instanceArrayItems, firstRegularItemIndex, consumer, JsonBundle.message("schema.validation.array.no.extra"))
      return
    }

    val unevaluatedItemsSchema = schema.unevaluatedItemsSchema
    if (unevaluatedItemsSchema != null) {
      validateAgainstNonPositionalSchema(unevaluatedItemsSchema, instanceArrayItems, firstRegularItemIndex, consumer, JsonBundle.message("schema.validation.array.no.unevaluated"))
    }
  }

  private fun validateAgainstNonPositionalSchema(nonPositionalItemsSchema: JsonSchemaObject,
                                                 instanceArrayItems: List<JsonValueAdapter>,
                                                 firstRegularItemIndex: Int,
                                                 consumer: JsonValidationHost,
                                                 errorMessage: @Nls String) {
    if (nonPositionalItemsSchema.constantSchema == true) {
      return
    }

    if (nonPositionalItemsSchema.constantSchema == false && instanceArrayItems.getOrNull(firstRegularItemIndex) != null) {
      consumer.error(errorMessage, instanceArrayItems[firstRegularItemIndex].delegate, JsonErrorPriority.LOW_PRIORITY)
      return
    }

    for (index in firstRegularItemIndex until instanceArrayItems.size) {
      val instanceArrayItem = instanceArrayItems.getOrNull(index) ?: break
      consumer.checkObjectBySchemaRecordErrors(nonPositionalItemsSchema, instanceArrayItem)
    }
  }
}