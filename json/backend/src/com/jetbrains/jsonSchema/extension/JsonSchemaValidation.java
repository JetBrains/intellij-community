// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JsonSchemaValidation {
  /**
   * Validates given property adapter against given JSON-schema node considering the validation options. Results are recorded by the provided consumer instance.
   *
   * @return FALSE if the inspected propValue has errors, TRUE if the propValue is valid.
   * The implementations might consider returning the value as soon as the first error is found, or continue processing all the possible errors.
   * This behaviour is controlled by the {@link JsonComplianceCheckerOptions#shouldStopValidationAfterAnyErrorFound()} method.
   */
  boolean validate(@NotNull JsonValueAdapter propValue,
                   @NotNull JsonSchemaObject schema,
                   @Nullable JsonSchemaType schemaType,
                   @NotNull JsonValidationHost consumer,
                   @NotNull JsonComplianceCheckerOptions options);
}
