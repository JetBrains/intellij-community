// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class WrapOnTypingAccessor extends CodeStyleFieldAccessor<Integer,Boolean> {
  WrapOnTypingAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Override
  protected @Nullable Boolean parseString(@NotNull String string) {
    return "true".equalsIgnoreCase(string);
  }

  @Override
  protected @NotNull Boolean toExternal(@NotNull Integer value) {
    return value == CommonCodeStyleSettings.WrapOnTyping.WRAP.intValue;
  }

  @Override
  protected Integer fromExternal(@NotNull Boolean extValue) {
    return extValue ?
           CommonCodeStyleSettings.WrapOnTyping.WRAP.intValue :
           CommonCodeStyleSettings.WrapOnTyping.NO_WRAP.intValue;
  }

  @Override
  protected boolean isEmpty(@NotNull Integer value) {
    return value < 0;
  }

  @Override
  protected @Nullable String valueToString(@NotNull Boolean value) {
    return String.valueOf(value);
  }
}
