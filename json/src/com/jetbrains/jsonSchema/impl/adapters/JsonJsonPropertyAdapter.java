// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.adapters;

import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public final class JsonJsonPropertyAdapter implements JsonPropertyAdapter {
  private final @NotNull JsonProperty myProperty;

  public JsonJsonPropertyAdapter(@NotNull JsonProperty property) {
    myProperty = property;
  }

  @Override
  public @Nullable String getName() {
    return myProperty.getName();
  }

  @Override
  public @NotNull Collection<JsonValueAdapter> getValues() {
    return myProperty.getValue() == null ? ContainerUtil.emptyList() : Collections.singletonList(createAdapterByType(myProperty.getValue()));
  }

  @Override
  public @Nullable JsonValueAdapter getNameValueAdapter() {
    return createAdapterByType(myProperty.getNameElement());
  }

  @Override
  public @NotNull PsiElement getDelegate() {
    return myProperty;
  }

  @Override
  public @Nullable JsonObjectValueAdapter getParentObject() {
    return myProperty.getParent() instanceof JsonObject ? new JsonJsonObjectAdapter((JsonObject)myProperty.getParent()) : null;
  }

  public static @NotNull JsonValueAdapter createAdapterByType(@NotNull JsonValue value) {
    if (value instanceof JsonObject) return new JsonJsonObjectAdapter((JsonObject)value);
    if (value instanceof JsonArray) return new JsonJsonArrayAdapter((JsonArray)value);
    return new JsonJsonGenericValueAdapter(value);
  }
}
