// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

final class WrappingAccessor extends MagicIntegerConstAccessor {
  WrappingAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field,
          new int[]{
            CommonCodeStyleSettings.DO_NOT_WRAP,
            CommonCodeStyleSettings.WRAP_AS_NEEDED,
            CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM,
            CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM,
            CommonCodeStyleSettings.WRAP_ALWAYS
          },
          new String[]{
            "off",
            "normal",
            "on_every_item",
            "on_every_item",
            "split_into_lines",
          });
  }
}
