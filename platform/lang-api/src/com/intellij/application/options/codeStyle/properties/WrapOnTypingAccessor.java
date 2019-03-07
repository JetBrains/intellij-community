// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

public class WrapOnTypingAccessor extends IntegerAccessor {
  WrapOnTypingAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @NotNull
  @Override
  protected String toExternal(@NotNull Integer value) {
    return value == CommonCodeStyleSettings.WrapOnTyping.WRAP.intValue ? "true" : "false";
  }

  @Override
  protected Integer fromExternal(@NotNull String str) {
    return "true".equalsIgnoreCase(str) ?
           CommonCodeStyleSettings.WrapOnTyping.WRAP.intValue :
           CommonCodeStyleSettings.WrapOnTyping.NO_WRAP.intValue;
  }
}
