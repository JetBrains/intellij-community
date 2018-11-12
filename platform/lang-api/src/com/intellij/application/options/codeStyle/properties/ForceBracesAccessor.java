// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

class ForceBracesAccessor extends CodeStylePropertyAccessor<Integer> {
  private final static BidirectionalMap<Integer, String> FORCE_BRACES_MAP = new BidirectionalMap<>();
  static {
    FORCE_BRACES_MAP.put(CommonCodeStyleSettings.DO_NOT_FORCE, "never");
    FORCE_BRACES_MAP.put(CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE, "if_multiline");
    FORCE_BRACES_MAP.put(CommonCodeStyleSettings.FORCE_BRACES_ALWAYS, "always");
  }

  ForceBracesAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
  }

  @Nullable
  @Override
  protected Integer parseString(@NotNull String str) {
    List<Integer> keys = FORCE_BRACES_MAP.getKeysByValue(str);
    return keys != null && keys.size() > 0 ? keys.get(0) : null;
  }

  @NotNull
  @Override
  protected String asString(@NotNull Integer value) {
    return FORCE_BRACES_MAP.get(value);
  }
}
