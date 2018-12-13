// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public class BooleanAccessor extends CodeStylePropertyAccessor<Boolean> implements CodeStyleChoiceList {

  private final static List<String> BOOLEAN_VALS = Arrays.asList("false", "true");

  public BooleanAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Nullable
  @Override
  protected Boolean parseString(@NotNull String str) {
    return str.equalsIgnoreCase("true");
  }

  @NotNull
  @Override
  protected String asString(@NotNull Boolean value) {
    return value.toString();
  }

  @NotNull
  @Override
  public List<String> getChoices() {
    return BOOLEAN_VALS;
  }
}
