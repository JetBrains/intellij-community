// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Field;
import java.util.List;

import static com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil.getValueList;

public abstract class ValueListPropertyAccessor<T> extends CodeStyleFieldAccessor<T, List<String>> implements CodeStyleValueList {
  public ValueListPropertyAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Override
  protected abstract @Nullable T fromExternal(@NotNull List<String> extVal);

  @Override
  protected abstract @NotNull List<String> toExternal(@NotNull T value);

  @Override
  protected @Unmodifiable @Nullable List<String> parseString(@NotNull String string) {
    return getValueList(string);
  }

}
