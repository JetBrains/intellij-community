// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public abstract class ExternalStringAccessor<T> extends CodeStyleFieldAccessor<T,String> {
  public ExternalStringAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Nullable
  @Override
  protected abstract T fromExternal(@NotNull String extVal);

  @NotNull
  @Override
  protected abstract String toExternal(@NotNull T value);

  @Nullable
  @Override
  protected String parseString(@NotNull String string) {
    return string;
  }

  @Override
  protected String valueToString(@NotNull String value) {
    return value;
  }
}
