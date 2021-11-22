// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

import static com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil.getValueList;

public abstract class ValueListPropertyAccessor<T> extends CodeStyleFieldAccessor<T, List<String>> implements CodeStyleValueList {
  public ValueListPropertyAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Nullable
  @Override
  protected abstract T fromExternal(@NotNull List<String> extVal);

  @NotNull
  @Override
  protected abstract List<String> toExternal(@NotNull T value);

  @Nullable
  @Override
  protected List<String> parseString(@NotNull String string) {
    return getValueList(string);
  }

}
