// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

public class IntegerAccessor extends CodeStylePropertyAccessor<Integer,String> {
  IntegerAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Override
  protected Integer fromExternal(@NotNull String str) {
    try {
      return Integer.parseInt(str);
    }
    catch (NumberFormatException nfe) {
      return null;
    }
  }

  @NotNull
  @Override
  protected String toExternal(@NotNull Integer value) {
    return value.toString();
  }

  @Override
  protected boolean isEmpty(@NotNull Integer value) {
    return value < 0;
  }
}

