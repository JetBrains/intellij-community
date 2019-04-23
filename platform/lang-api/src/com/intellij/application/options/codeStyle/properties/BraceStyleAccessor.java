// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

class BraceStyleAccessor extends MagicIntegerConstAccessor {

  BraceStyleAccessor(@NotNull Object object, @NotNull Field field) {
    super(object,
          field,
          new int[]{
            CommonCodeStyleSettings.END_OF_LINE,
            CommonCodeStyleSettings.NEXT_LINE,
            CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED,
            CommonCodeStyleSettings.NEXT_LINE_SHIFTED,
            CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
          },
          new String[]{
            "end_of_line",
            "next_line",
            "next_line_if_wrapped",
            "whitesmiths",
            "gnu"
          });
  }

}
