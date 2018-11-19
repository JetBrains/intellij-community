// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

class WrappingAccessor extends CodeStylePropertyAccessor<Integer> {
  private final static BidirectionalMap<Integer, String> WRAPPING_MAP = new BidirectionalMap<>();
  static {
    WRAPPING_MAP.put(CommonCodeStyleSettings.DO_NOT_WRAP, "never");
    WRAPPING_MAP.put(CommonCodeStyleSettings.WRAP_AS_NEEDED, "if_long");
    WRAPPING_MAP.put(CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM, "on_every_item");
    WRAPPING_MAP.put(CommonCodeStyleSettings.WRAP_ALWAYS, "always");
  }

  WrappingAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Nullable
  @Override
  protected Integer parseString(@NotNull String str) {
    List<Integer> keys = WRAPPING_MAP.getKeysByValue(str);
    return keys != null && keys.size() > 0 ? keys.get(0) : null;
  }

  @NotNull
  @Override
  protected String asString(@NotNull Integer value) {
    return WRAPPING_MAP.get(value);
  }
}
