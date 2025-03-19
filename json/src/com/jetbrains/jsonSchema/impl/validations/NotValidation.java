// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations;

import com.intellij.json.JsonBundle;
import com.jetbrains.jsonSchema.extension.JsonAnnotationsCollectionMode;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.MatchResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class NotValidation implements JsonSchemaValidation {
  public static final NotValidation INSTANCE = new NotValidation();
  @Override
  public boolean validate(@NotNull JsonValueAdapter propValue,
                          @NotNull JsonSchemaObject schema,
                          @Nullable JsonSchemaType schemaType,
                          @NotNull JsonValidationHost consumer,
                          @NotNull JsonComplianceCheckerOptions options) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.NotValidation);
    final MatchResult result = consumer.resolve(schema.getNot(), propValue);
    if (result.mySchemas.isEmpty() && result.myExcludingSchemas.isEmpty()) return true;

    // if 'not' uses reference to owning schema back -> do not check, seems it does not make any sense
    if (result.mySchemas.stream().anyMatch(s -> schema.equals(s)) ||
        result.myExcludingSchemas.stream().flatMap(Collection::stream)
          .anyMatch(s -> schema.equals(s))) return true;

    final JsonValidationHost checker =
      consumer.checkByMatchResult(propValue,
                                  result,
                                  new JsonComplianceCheckerOptions(options.isCaseInsensitiveEnumCheck(), true, false,
                                                                   JsonAnnotationsCollectionMode.FIND_FIRST));
    if (checker == null || checker.isValid()) {
      consumer.error(JsonBundle.message("schema.validation.against.not"), propValue.getDelegate(), JsonErrorPriority.NOT_SCHEMA);
      return false;
    }

    return true;
  }
}
