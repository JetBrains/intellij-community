// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class StringAccessor extends ExternalStringAccessor<String> {

  StringAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Override
  protected @Nullable String fromExternal(@NotNull String str) {
    return str;
  }

  @Override
  protected @NotNull String toExternal(@NotNull String value) {
    return value;
  }

  @Override
  protected boolean isEmpty(@NotNull String value) {
    return StringUtil.isEmptyOrSpaces(value);
  }
}
