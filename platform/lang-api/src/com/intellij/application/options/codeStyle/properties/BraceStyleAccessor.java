// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

class BraceStyleAccessor extends CodeStylePropertyAccessor<Integer> {
  private final static BidirectionalMap<Integer, String> BRACE_STYLE_MAP = new BidirectionalMap<>();
  static {
    BRACE_STYLE_MAP.put(CommonCodeStyleSettings.END_OF_LINE, "end_of_line");
    BRACE_STYLE_MAP.put(CommonCodeStyleSettings.NEXT_LINE, "next_line");
    BRACE_STYLE_MAP.put(CommonCodeStyleSettings.NEXT_LINE_SHIFTED, "whitesmiths");
    BRACE_STYLE_MAP.put(CommonCodeStyleSettings.NEXT_LINE_SHIFTED2, "gnu");
  }

  BraceStyleAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Nullable
  @Override
  protected Integer parseString(@NotNull String str) {
    List<Integer> keys = BRACE_STYLE_MAP.getKeysByValue(str);
    return keys != null && keys.size() > 0 ? keys.get(0) : null;
  }

  @NotNull
  @Override
  protected String asString(@NotNull Integer value) {
    return BRACE_STYLE_MAP.get(value);
  }
}
