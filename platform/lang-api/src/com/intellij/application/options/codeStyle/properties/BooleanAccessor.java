// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public class BooleanAccessor extends CodeStyleFieldAccessor<Boolean,Boolean> implements CodeStyleChoiceList {

  final static List<String> BOOLEAN_VALS = Arrays.asList("false", "true");

  public BooleanAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Nullable
  @Override
  protected Boolean parseString(@NotNull String string) {
    return "true".equalsIgnoreCase(string);
  }

  @Nullable
  @Override
  protected Boolean fromExternal(@NotNull Boolean extVal) {
    return extVal;
  }

  @NotNull
  @Override
  protected Boolean toExternal(@NotNull Boolean value) {
    return value;
  }

  @NotNull
  @Override
  public List<String> getChoices() {
    return BOOLEAN_VALS;
  }

  @Nullable
  @Override
  protected String valueToString(@NotNull Boolean value) {
    return String.valueOf(value);
  }
}
