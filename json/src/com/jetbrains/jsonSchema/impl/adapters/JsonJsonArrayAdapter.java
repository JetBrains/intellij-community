/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl.adapters;

import com.intellij.json.psi.JsonArray;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 2/20/2017.
 */
public class JsonJsonArrayAdapter implements JsonArrayValueAdapter {
  @NotNull private final JsonArray myArray;

  public JsonJsonArrayAdapter(@NotNull JsonArray array) {myArray = array;}

  @Override
  public boolean isObject() {
    return false;
  }

  @Override
  public boolean isArray() {
    return true;
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

  @NotNull
  @Override
  public PsiElement getDelegate() {
    return myArray;
  }

  @Nullable
  @Override
  public JsonObjectValueAdapter getAsObject() {
    return null;
  }

  @Nullable
  @Override
  public JsonArrayValueAdapter getAsArray() {
    return this;
  }

  @NotNull
  @Override
  public List<JsonValueAdapter> getElements() {
    return myArray.getValueList().stream().filter(e -> e != null).map(e -> JsonJsonPropertyAdapter.createAdapterByType(e)).collect(
      Collectors.toList());
  }
}
