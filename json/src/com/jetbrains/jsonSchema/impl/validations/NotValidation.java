// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations;

import com.intellij.json.JsonBundle;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.MatchResult;

import java.util.Collection;

public final class NotValidation implements JsonSchemaValidation {
  public static final NotValidation INSTANCE = new NotValidation();
  @Override
  public void validate(JsonValueAdapter propValue,
                       JsonSchemaObject schema,
                       JsonSchemaType schemaType,
                       JsonValidationHost consumer,
                       JsonComplianceCheckerOptions options) {
    final MatchResult result = consumer.resolve(schema.getNot());
    if (result.mySchemas.isEmpty() && result.myExcludingSchemas.isEmpty()) return;

    // if 'not' uses reference to owning schema back -> do not check, seems it does not make any sense
    if (result.mySchemas.stream().anyMatch(s -> schema.equals(s)) ||
        result.myExcludingSchemas.stream().flatMap(Collection::stream)
          .anyMatch(s -> schema.equals(s))) return;

    final JsonValidationHost checker = consumer.checkByMatchResult(propValue, result, options.withForcedStrict());
    if (checker == null || checker.isValid()) consumer.error(JsonBundle.message("schema.validation.against.not"), propValue.getDelegate(), JsonErrorPriority.NOT_SCHEMA);
  }
}
