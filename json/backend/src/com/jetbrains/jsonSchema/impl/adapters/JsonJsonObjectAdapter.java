// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.adapters;

import com.intellij.json.psi.JsonObject;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public final class JsonJsonObjectAdapter implements JsonObjectValueAdapter {
  private final @NotNull JsonObject myValue;

  public JsonJsonObjectAdapter(@NotNull JsonObject value) {myValue = value;}

  @Override
  public boolean isObject() {
    return true;
  }

  @Override
  public boolean isArray() {
    return false;
  }

  @Override
  public boolean isStringLiteral() {
    return false;
  }

  @Override
  public boolean isNumberLiteral() {
    return false;
  }

  @Override
  public boolean isBooleanLiteral() {
    return false;
  }

  @Override
  public @NotNull PsiElement getDelegate() {
    return myValue;
  }

  @Override
  public @Nullable JsonObjectValueAdapter getAsObject() {
    return this;
  }

  @Override
  public @Nullable JsonArrayValueAdapter getAsArray() {
    return null;
  }

  @Override
  public @NotNull List<JsonPropertyAdapter> getPropertyList() {
    return myValue.getPropertyList().stream().filter(p -> p != null)
      .map(p -> new JsonJsonPropertyAdapter(p)).collect(Collectors.toList());
  }
}
