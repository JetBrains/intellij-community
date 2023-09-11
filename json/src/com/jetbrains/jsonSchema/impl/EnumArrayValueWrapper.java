// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class EnumArrayValueWrapper {
  private final Object @NotNull [] myValues;

  public EnumArrayValueWrapper(Object @NotNull [] values) {
    myValues = values;
  }

  public Object @NotNull [] getValues() {
    return myValues;
  }

  @Override
  public String toString() {
    return "[" + Arrays.stream(myValues).map(v -> v.toString()).collect(Collectors.joining(", ")) + "]";
  }
}
