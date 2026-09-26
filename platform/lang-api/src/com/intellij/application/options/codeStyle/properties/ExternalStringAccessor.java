// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public abstract class ExternalStringAccessor<T> extends CodeStyleFieldAccessor<T,String> {
  public ExternalStringAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Override
  protected abstract @Nullable T fromExternal(@NotNull String extVal);

  @Override
  protected abstract @NotNull String toExternal(@NotNull T value);

  @Override
  protected @Nullable String parseString(@NotNull String string) {
    return string;
  }

  @Override
  protected String valueToString(@NotNull String value) {
    return value;
  }
}
