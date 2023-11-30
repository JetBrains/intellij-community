// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.adapters;

import com.intellij.json.psi.*;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonJsonGenericValueAdapter implements JsonValueAdapter {
  private final @NotNull JsonValue myValue;

  public JsonJsonGenericValueAdapter(@NotNull JsonValue value) {myValue = value;}

  @Override
  public boolean isObject() {
    return false;
  }

  @Override
  public boolean isArray() {
    return false;
  }

  @Override
  public boolean isStringLiteral() {
    return myValue instanceof JsonStringLiteral;
  }

  @Override
  public boolean isNumberLiteral() {
    return myValue instanceof JsonNumberLiteral;
  }

  @Override
  public boolean isBooleanLiteral() {
    return myValue instanceof JsonBooleanLiteral;
  }

  @Override
  public boolean isNull() {
    return myValue instanceof JsonNullLiteral;
  }

  @Override
  public @NotNull PsiElement getDelegate() {
    return myValue;
  }

  @Override
  public @Nullable JsonObjectValueAdapter getAsObject() {
    return null;
  }

  @Override
  public @Nullable JsonArrayValueAdapter getAsArray() {
    return null;
  }
}
