// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class IntegerAccessor extends CodeStyleFieldAccessor<Integer,Integer> {
  public IntegerAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Override
  protected @Nullable Integer parseString(@NotNull String string) {
    try {
      return Integer.parseInt(string);
    }
    catch (NumberFormatException nfe) {
      return null;
    }
  }

  @Override
  protected @Nullable String valueToString(@NotNull Integer value) {
    return String.valueOf(value);
  }

  @Override
  protected Integer fromExternal(@NotNull Integer i) {
    return i;
  }

  @Override
  protected @NotNull Integer toExternal(@NotNull Integer value) {
    return value;
  }

  @Override
  protected boolean isEmpty(@NotNull Integer value) {
    return value < 0;
  }
}

