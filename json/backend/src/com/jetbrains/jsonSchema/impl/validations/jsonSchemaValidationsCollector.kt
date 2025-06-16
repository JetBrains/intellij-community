// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations

import com.jetbrains.jsonSchema.extension.JsonSchemaValidation
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils

internal fun getSchema7AndEarlierValidations(schema: JsonSchemaObject,
                                   type: JsonSchemaType?,
                                   value: JsonValueAdapter): Sequence<JsonSchemaValidation> {
  return sequence {
    if (type != null) yieldAll(getTypeValidations(type))
    yieldAll(getBaseValidations(schema, value))
  }.distinct()
}

internal fun getTypeValidations(type: JsonSchemaType): Sequence<JsonSchemaValidation> {
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
        yield(ArrayValidation.INSTANCE)
      }
      JsonSchemaType._object -> {
        yield(ObjectValidation.INSTANCE)
      }
      else -> {}
    }
  }
}

internal fun getBaseValidations(schema: JsonSchemaObject, value: JsonValueAdapter): Sequence<JsonSchemaValidation> {
  if (schema.constantSchema != null) {
    return sequenceOf(ConstantSchemaValidation)
  }

  return sequence {
    yield(EnumValidation.INSTANCE)
    if (!value.isShouldBeIgnored) {
      if (JsonSchemaObjectReadingUtils.hasNumericChecks(schema) && value.isNumberLiteral) {
        yield(NumericValidation.INSTANCE)
      }
      if (JsonSchemaObjectReadingUtils.hasStringChecks(schema) && value.isStringLiteral) {
        yield(StringValidation.INSTANCE)
      }
      if (JsonSchemaObjectReadingUtils.hasArrayChecks(schema) && value.isArray) {
        yield(ArrayValidation.INSTANCE)
      }
      if (hasMinMaxLengthChecks(schema)) {
        if (value.isStringLiteral) {
          yield(StringValidation.INSTANCE)
        }
        else if (value.isArray) {
          yield(ArrayValidation.INSTANCE)
        }
      }
      if (JsonSchemaObjectReadingUtils.hasObjectChecks(schema) && value.isObject) {
        yield(ObjectValidation.INSTANCE)
      }
    }
    if (schema.not != null) {
      yield(NotValidation.INSTANCE)
    }
  }
}

internal fun hasMinMaxLengthChecks(schema: JsonSchemaObject): Boolean {
  return schema.minLength != null || schema.maxLength != null
}