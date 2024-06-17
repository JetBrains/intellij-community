// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JsonSchemaValidation {
  void validate(@NotNull JsonValueAdapter propValue,
                @NotNull JsonSchemaObject schema,
                @Nullable JsonSchemaType schemaType,
                @NotNull JsonValidationHost consumer,
                @NotNull JsonComplianceCheckerOptions options);
}
