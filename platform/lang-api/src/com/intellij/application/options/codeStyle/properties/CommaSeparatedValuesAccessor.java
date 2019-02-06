// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

public class CommaSeparatedValuesAccessor extends ValueListPropertyAccessor<String> {
  public CommaSeparatedValuesAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Nullable
  @Override
  protected String fromExternal(@NotNull List<String> extVal) {
    StringBuilder valueBuilder = new StringBuilder();
    for (String value : extVal) {
      if (valueBuilder.length() > 0) {
        valueBuilder.append(",");
      }
      valueBuilder.append(value);
    }
    return valueBuilder.toString();
  }

  @NotNull
  @Override
  protected List<String> toExternal(@NotNull String value) {
    return getValueList(value);
  }
}
