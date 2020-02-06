// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl.validations;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;

import static com.jetbrains.jsonSchema.impl.JsonSchemaAnnotatorChecker.getValue;

public class StringValidation implements JsonSchemaValidation {
  public static final StringValidation INSTANCE = new StringValidation();
  @Override
  public void validate(JsonValueAdapter propValue,
                       JsonSchemaObject schema,
                       JsonSchemaType schemaType,
                       JsonValidationHost consumer,
                       JsonComplianceCheckerOptions options) {
    checkString(propValue.getDelegate(), schema, consumer);
  }

  private static void checkString(PsiElement propValue,
                                  JsonSchemaObject schema,
                                  JsonValidationHost consumer) {
    String v = getValue(propValue, schema);
    if (v == null) return;
    final String value = StringUtil.unquoteString(v);
    if (schema.getMinLength() != null) {
      if (value.length() < schema.getMinLength()) {
        consumer.error("String is shorter than " + schema.getMinLength(), propValue, JsonErrorPriority.LOW_PRIORITY);
        return;
      }
    }
    if (schema.getMaxLength() != null) {
      if (value.length() > schema.getMaxLength()) {
        consumer.error("String is longer than " + schema.getMaxLength(), propValue, JsonErrorPriority.LOW_PRIORITY);
        return;
      }
    }
    if (schema.getPattern() != null) {
      if (schema.getPatternError() != null) {
        consumer.error("Can not check string by pattern because of error: " + StringUtil.convertLineSeparators(schema.getPatternError()),
              propValue, JsonErrorPriority.LOW_PRIORITY);
      }
      if (!schema.checkByPattern(value)) {
        consumer.error("String is violating the pattern: '" + StringUtil.convertLineSeparators(schema.getPattern()) + "'", propValue, JsonErrorPriority.LOW_PRIORITY);
      }
    }
    // I think we are not gonna to support format, there are a couple of RFCs there to check upon..
    /*
    if (schema.getFormat() != null) {
      LOG.info("Unsupported property used: 'format'");
    }*/
  }
}
