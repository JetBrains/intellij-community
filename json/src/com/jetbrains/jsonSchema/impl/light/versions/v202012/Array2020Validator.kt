// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions.v202012

import com.intellij.json.JsonBundle
import com.jetbrains.jsonSchema.extension.JsonErrorPriority
import com.jetbrains.jsonSchema.extension.JsonValidationHost
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.validations.ArrayValidation
import org.jetbrains.annotations.Nls

internal object Array2020Validator : ArrayValidation() {
  override fun validateIndividualItems(instanceArrayItems: List<JsonValueAdapter>, schema: JsonSchemaObject, consumer: JsonValidationHost, options: JsonComplianceCheckerOptions): Boolean {
    val additionalItemsSchemaList = schema.itemsSchemaList
    val firstRegularItemIndex = if (additionalItemsSchemaList.isNullOrEmpty()) 0 else additionalItemsSchemaList.size

    var isValid = true

    // check instance items with positional schema
    for (index in 0 until firstRegularItemIndex) {
      val positionalSchema = additionalItemsSchemaList?.get(index) ?: break
      val inspectedInstanceItem = instanceArrayItems.getOrNull(index) ?: break
      consumer.checkObjectBySchemaRecordErrors(positionalSchema, inspectedInstanceItem)

      isValid = isValid && consumer.errors.isEmpty()
      if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false
    }

    // check the rest of instance items with regular schema
    val additionalItemsSchema = schema.additionalItemsSchema
    if (additionalItemsSchema != null) {
      isValid = isValid && validateAgainstNonPositionalSchema(additionalItemsSchema, instanceArrayItems, firstRegularItemIndex, consumer, options, JsonBundle.message("schema.validation.array.no.extra"))
      if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false
    }

    val unevaluatedItemsSchema = schema.unevaluatedItemsSchema
    if (unevaluatedItemsSchema != null) {
      isValid = isValid && validateAgainstNonPositionalSchema(unevaluatedItemsSchema, instanceArrayItems, firstRegularItemIndex, consumer, options, JsonBundle.message("schema.validation.array.no.unevaluated"))
      if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false
    }

    return isValid
  }

  private fun validateAgainstNonPositionalSchema(
    nonPositionalItemsSchema: JsonSchemaObject,
    instanceArrayItems: List<JsonValueAdapter>,
    firstRegularItemIndex: Int,
    consumer: JsonValidationHost,
    options: JsonComplianceCheckerOptions,
    errorMessage: @Nls String,
  ): Boolean {
    if (nonPositionalItemsSchema.constantSchema == true) {
      return true
    }

    if (nonPositionalItemsSchema.constantSchema == false && instanceArrayItems.getOrNull(firstRegularItemIndex) != null) {
      consumer.error(errorMessage, instanceArrayItems[firstRegularItemIndex].delegate, JsonErrorPriority.LOW_PRIORITY)
      return false
    }

    var isValid = true
    for (index in firstRegularItemIndex until instanceArrayItems.size) {
      val instanceArrayItem = instanceArrayItems.getOrNull(index) ?: break
      consumer.checkObjectBySchemaRecordErrors(nonPositionalItemsSchema, instanceArrayItem)

      isValid = isValid && consumer.errors.isEmpty()
      if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false
    }
    return isValid
  }
}