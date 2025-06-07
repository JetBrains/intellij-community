// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations;

import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaAnnotatorChecker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public final class TypeValidation implements JsonSchemaValidation {
  public static final TypeValidation INSTANCE = new TypeValidation();
  @Override
  public boolean validate(@NotNull JsonValueAdapter propValue,
                          @NotNull JsonSchemaObject schema,
                          @Nullable JsonSchemaType schemaType,
                          @NotNull JsonValidationHost consumer,
                          @NotNull JsonComplianceCheckerOptions options) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.TypeValidation);
    JsonSchemaType otherType = JsonSchemaAnnotatorChecker.getMatchingSchemaType(schema, schemaType);
    if (otherType != null && !otherType.equals(schemaType) && !otherType.equals(propValue.getAlternateType(schemaType))) {
      consumer.typeError(propValue.getDelegate(), propValue.getAlternateType(schemaType), JsonSchemaAnnotatorChecker.getExpectedTypes(Collections.singleton(schema)));
      return false;
    }
    return true;
  }
}
