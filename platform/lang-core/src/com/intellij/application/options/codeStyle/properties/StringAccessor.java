// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

class StringAccessor extends ExternalStringAccessor<String> {

  StringAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Nullable
  @Override
  protected String fromExternal(@NotNull String str) {
    return str;
  }

  @NotNull
  @Override
  protected String toExternal(@NotNull String value) {
    return value;
  }

  @Override
  protected boolean isEmpty(@NotNull String value) {
    return StringUtil.isEmptyOrSpaces(value);
  }
}
