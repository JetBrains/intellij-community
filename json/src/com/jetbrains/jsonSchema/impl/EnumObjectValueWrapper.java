// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.stream.Collectors;

public class EnumObjectValueWrapper {
  @NotNull private final Map<String, Object> myValues;

  public EnumObjectValueWrapper(@NotNull Map<String, Object> values) {
    myValues = values;
  }

  @NotNull
  public Map<String, Object> getValues() {
    return myValues;
  }

  @Override
  public String toString() {
    return "{" + myValues.entrySet().stream().map(v -> "\"" + v.getKey() + "\": " + v.getValue()).collect(Collectors.joining(", ")) + "}";
  }
}
