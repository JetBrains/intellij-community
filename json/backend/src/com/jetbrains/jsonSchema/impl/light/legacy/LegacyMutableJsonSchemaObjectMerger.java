// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.legacy;

import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaObjectImpl;
import org.jetbrains.annotations.NotNull;

@Deprecated
public class LegacyMutableJsonSchemaObjectMerger implements JsonSchemaObjectMerger {
  @Override
  public @NotNull JsonSchemaObject mergeObjects(@NotNull JsonSchemaObject base, @NotNull JsonSchemaObject other, @NotNull JsonSchemaObject pointTo) {
    return JsonSchemaObjectImpl.merge(((JsonSchemaObjectImpl)base), ((JsonSchemaObjectImpl)other), ((JsonSchemaObjectImpl)pointTo));
  }
}
