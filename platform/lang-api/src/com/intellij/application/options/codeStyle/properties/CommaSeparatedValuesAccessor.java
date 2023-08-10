// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

import static com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil.getValueList;
import static com.intellij.application.options.codeStyle.properties.CodeStylePropertiesUtil.toCommaSeparatedString;

public class CommaSeparatedValuesAccessor extends ValueListPropertyAccessor<String> {
  public CommaSeparatedValuesAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Nullable
  @Override
  protected String fromExternal(@NotNull List<String> extVal) {
    return toCommaSeparatedString(extVal);
  }

  @NotNull
  @Override
  protected List<String> toExternal(@NotNull String value) {
    return getValueList(value);
  }

  @Nullable
  @Override
  protected String valueToString(@NotNull List<String> value) {
    return fromExternal(value);
  }


  @Override
  public boolean isEmptyListAllowed() {
    return true;
  }
}
