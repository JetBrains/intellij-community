// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

class WrappingAccessor extends CodeStylePropertyAccessor<Integer> implements CodeStyleChoiceList {
  private final static BidirectionalMap<Integer, String> WRAPPING_MAP = new BidirectionalMap<>();

  public static final String VALUE_OFF = "off";
  public static final String VALUE_NORMAL = "normal";
  public static final String VALUE_ON_EVERY_ITEM = "on_every_item";
  public static final String VALUE_SPLIT_INTO_LINES = "split_into_lines";

  public final static List<String> ALL_VALUES = Arrays.asList(VALUE_OFF, VALUE_NORMAL, VALUE_ON_EVERY_ITEM, VALUE_SPLIT_INTO_LINES);

  static {
    WRAPPING_MAP.put(CommonCodeStyleSettings.DO_NOT_WRAP, VALUE_OFF);
    WRAPPING_MAP.put(CommonCodeStyleSettings.WRAP_AS_NEEDED, VALUE_NORMAL);
    WRAPPING_MAP.put(CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM, VALUE_ON_EVERY_ITEM);
    WRAPPING_MAP.put(CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM, VALUE_ON_EVERY_ITEM);
    WRAPPING_MAP.put(CommonCodeStyleSettings.WRAP_ALWAYS, VALUE_SPLIT_INTO_LINES);
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

  @NotNull
  @Override
  public List<String> getChoices() {
    return ALL_VALUES;
  }
}
