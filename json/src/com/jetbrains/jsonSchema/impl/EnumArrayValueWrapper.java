// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

public class EnumArrayValueWrapper {
  @NotNull private final Object[] myValues;

  public EnumArrayValueWrapper(@NotNull Object[] values) {
    myValues = values;
  }

  @NotNull
  public Object[] getValues() {
    return myValues;
  }

  @Override
  public String toString() {
    return "[" + Arrays.stream(myValues).map(v -> v.toString()).collect(Collectors.joining(", ")) + "]";
  }
}
