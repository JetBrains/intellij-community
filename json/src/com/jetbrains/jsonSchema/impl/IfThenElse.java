// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IfThenElse {
  private final JsonSchemaObject condition;
  private final JsonSchemaObject trueBranch;
  private final JsonSchemaObject falseBranch;

  public IfThenElse(JsonSchemaObject condition, JsonSchemaObject trueBranch, JsonSchemaObject falseBranch) {
    this.condition = condition;
    this.trueBranch = trueBranch;
    this.falseBranch = falseBranch;
  }

  public @NotNull JsonSchemaObject getIf() {
    return condition;
  }

  public @Nullable JsonSchemaObject getThen() {
    return trueBranch;
  }

  public @Nullable JsonSchemaObject getElse() {
    return falseBranch;
  }
}
