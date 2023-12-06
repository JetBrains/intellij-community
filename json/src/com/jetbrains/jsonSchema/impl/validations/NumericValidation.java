// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations;

import com.intellij.json.JsonBundle;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.*;

public final class NumericValidation implements JsonSchemaValidation {
  public static final NumericValidation INSTANCE = new NumericValidation();
  private static void checkNumber(PsiElement propValue,
                                  JsonSchemaObject schema,
                                  JsonSchemaType schemaType,
                                  JsonValidationHost consumer) {
    Number value;
    String valueText = JsonSchemaAnnotatorChecker.getValue(propValue, schema);
    if (valueText == null) return;
    if (JsonSchemaType._integer.equals(schemaType)) {
      value = JsonSchemaType.getIntegerValue(valueText);
      if (value == null) {
        consumer.error(JsonBundle.message("schema.validation.integer.expected"), propValue,
                       JsonValidationError.FixableIssueKind.TypeMismatch,
                       new JsonValidationError.TypeMismatchIssueData(new JsonSchemaType[]{schemaType}), JsonErrorPriority.TYPE_MISMATCH);
        return;
      }
    }
    else {
      try {
        value = Double.valueOf(valueText);
      }
      catch (NumberFormatException e) {
        if (!JsonSchemaType._string_number.equals(schemaType)) {
          consumer.error(JsonBundle.message("schema.validation.number.expected"), propValue,
                JsonValidationError.FixableIssueKind.TypeMismatch,
                new JsonValidationError.TypeMismatchIssueData(new JsonSchemaType[]{schemaType}), JsonErrorPriority.TYPE_MISMATCH);
        }
        return;
      }
    }
    final Number multipleOf = schema.getMultipleOf();
    if (multipleOf != null) {
      final double leftOver = value.doubleValue() % multipleOf.doubleValue();
      if (leftOver > 0.000001) {
        final String multipleOfValue = String.valueOf(Math.abs(multipleOf.doubleValue() - multipleOf.intValue()) < 0.000001 ?
                                                      multipleOf.intValue() : multipleOf);
        consumer.error(JsonBundle.message("schema.validation.not.multiple.of", multipleOfValue), propValue, JsonErrorPriority.LOW_PRIORITY);
        return;
      }
    }

    checkMinimum(schema, value, propValue, consumer);
    checkMaximum(schema, value, propValue, consumer);
  }

  private static void checkMaximum(JsonSchemaObject schema,
                                   Number value,
                                   PsiElement propertyValue,
                                   JsonValidationHost consumer) {

    Number exclusiveMaximumNumber = schema.getExclusiveMaximumNumber();
    if (exclusiveMaximumNumber != null) {
      final double doubleValue = exclusiveMaximumNumber.doubleValue();
      if (value.doubleValue() >= doubleValue) {
        consumer.error(JsonBundle.message("schema.validation.greater.than.exclusive.maximum", exclusiveMaximumNumber), propertyValue, JsonErrorPriority.LOW_PRIORITY);
      }
    }
    Number maximum = schema.getMaximum();
    if (maximum == null) return;
    boolean isExclusive = Boolean.TRUE.equals(schema.isExclusiveMaximum());
    final double doubleValue = maximum.doubleValue();
    if (isExclusive) {
      if (value.doubleValue() >= doubleValue) {
        consumer.error(JsonBundle.message("schema.validation.greater.than.exclusive.maximum", maximum), propertyValue, JsonErrorPriority.LOW_PRIORITY);
      }
    }
    else {
      if (value.doubleValue() > doubleValue) {
        consumer.error(JsonBundle.message("schema.validation.greater.than.maximum", maximum), propertyValue, JsonErrorPriority.LOW_PRIORITY);
      }
    }
  }

  private static void checkMinimum(JsonSchemaObject schema,
                                   Number value,
                                   PsiElement propertyValue,
                                   JsonValidationHost consumer) {
    // schema v6 - exclusiveMinimum is numeric now
    Number exclusiveMinimumNumber = schema.getExclusiveMinimumNumber();
    if (exclusiveMinimumNumber != null) {
      final double doubleValue = exclusiveMinimumNumber.doubleValue();
      if (value.doubleValue() <= doubleValue) {
        consumer.error(JsonBundle.message("schema.validation.less.than.exclusive.minimum", exclusiveMinimumNumber), propertyValue, JsonErrorPriority.LOW_PRIORITY);
      }
    }

    Number minimum = schema.getMinimum();
    if (minimum == null) return;
    boolean isExclusive = Boolean.TRUE.equals(schema.isExclusiveMinimum());
    final double doubleValue = minimum.doubleValue();
    if (isExclusive) {
      if (value.doubleValue() <= doubleValue) {
        consumer.error(JsonBundle.message("schema.validation.less.than.exclusive.minimum", minimum), propertyValue, JsonErrorPriority.LOW_PRIORITY);
      }
    }
    else {
      if (value.doubleValue() < doubleValue) {
        consumer.error(JsonBundle.message("schema.validation.less.than.minimum", minimum), propertyValue, JsonErrorPriority.LOW_PRIORITY);
      }
    }
  }

  @Override
  public void validate(JsonValueAdapter propValue,
                       JsonSchemaObject schema,
                       JsonSchemaType schemaType,
                       JsonValidationHost consumer,
                       JsonComplianceCheckerOptions options) {
    checkNumber(propValue.getDelegate(), schema, schemaType, consumer);
  }
}
