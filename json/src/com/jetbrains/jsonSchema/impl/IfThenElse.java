// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IfThenElse {
  private final JsonSchemaObject condition;
  private final JsonSchemaObject trueBranch;
  private final JsonSchemaObject falseBranch;

  public IfThenElse(JsonSchemaObject condition, JsonSchemaObject trueBranch, JsonSchemaObject falseBranch) {
    this.condition = condition;
    this.trueBranch = trueBranch;
    this.falseBranch = falseBranch;
  }

  @NotNull
  public JsonSchemaObject getIf() {
    return condition;
  }

  @Nullable
  public JsonSchemaObject getThen() {
    return trueBranch;
  }

  @Nullable
  public JsonSchemaObject getElse() {
    return falseBranch;
  }
}
