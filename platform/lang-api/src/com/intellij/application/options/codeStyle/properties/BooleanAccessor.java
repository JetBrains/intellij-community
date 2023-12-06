// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public class BooleanAccessor extends CodeStyleFieldAccessor<Boolean,Boolean> implements CodeStyleChoiceList {

  static final List<String> BOOLEAN_VALS = Arrays.asList("false", "true");

  public BooleanAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Override
  protected @Nullable Boolean parseString(@NotNull String string) {
    return "true".equalsIgnoreCase(string);
  }

  @Override
  protected @Nullable Boolean fromExternal(@NotNull Boolean extVal) {
    return extVal;
  }

  @Override
  protected @NotNull Boolean toExternal(@NotNull Boolean value) {
    return value;
  }

  @Override
  public @NotNull List<String> getChoices() {
    return BOOLEAN_VALS;
  }

  @Override
  protected @Nullable String valueToString(@NotNull Boolean value) {
    return String.valueOf(value);
  }
}
