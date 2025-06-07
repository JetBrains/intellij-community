// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Field;
import java.util.List;

import static com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil.getValueList;
import static com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil.toCommaSeparatedString;

public class CommaSeparatedValuesAccessor extends ValueListPropertyAccessor<String> {
  public CommaSeparatedValuesAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Override
  protected @Nullable String fromExternal(@NotNull List<String> extVal) {
    return toCommaSeparatedString(extVal);
  }

  @Override
  protected @Unmodifiable @NotNull List<String> toExternal(@NotNull String value) {
    return getValueList(value);
  }

  @Override
  protected @Nullable String valueToString(@NotNull List<String> value) {
    return fromExternal(value);
  }


  @Override
  public boolean isEmptyListAllowed() {
    return true;
  }
}
