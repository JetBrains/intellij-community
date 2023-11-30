// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations;

import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.*;

import java.util.List;

public final class IfThenElseValidation implements JsonSchemaValidation {
  public static final IfThenElseValidation INSTANCE = new IfThenElseValidation();
  @Override
  public void validate(JsonValueAdapter propValue,
                       JsonSchemaObject schema,
                       JsonSchemaType schemaType,
                       JsonValidationHost consumer,
                       JsonComplianceCheckerOptions options) {
    List<IfThenElse> ifThenElseList = schema.getIfThenElse();
    assert ifThenElseList != null;
    for (IfThenElse ifThenElse : ifThenElseList) {
      MatchResult result = consumer.resolve(ifThenElse.getIf());
      if (result.mySchemas.isEmpty() && result.myExcludingSchemas.isEmpty()) return;

      final JsonValidationHost checker = consumer.checkByMatchResult(propValue, result, options.withForcedStrict());
      if (checker != null) {
        if (checker.isValid()) {
          JsonSchemaObject then = ifThenElse.getThen();
          if (then != null) {
            consumer.checkObjectBySchemaRecordErrors(then, propValue);
          }
        }
        else {
          JsonSchemaObject schemaElse = ifThenElse.getElse();
          if (schemaElse != null) {
            consumer.checkObjectBySchemaRecordErrors(schemaElse, propValue);
          }
        }
      }
    }
  }
}
