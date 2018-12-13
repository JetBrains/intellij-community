// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

class ForceBracesAccessor extends CodeStylePropertyAccessor<Integer> implements CodeStyleChoiceList {

  private final static BidirectionalMap<Integer, String> FORCE_BRACES_MAP = new BidirectionalMap<>();

  public static final String VALUE_NEVER = "never";
  public static final String VALUE_MULTILINE = "if_multiline";
  public static final String VALUE_ALWAYS = "always";

  private final static List<String> ALL_VALUES = Arrays.asList(VALUE_NEVER, VALUE_MULTILINE, VALUE_ALWAYS);

  static {
    FORCE_BRACES_MAP.put(CommonCodeStyleSettings.DO_NOT_FORCE, VALUE_NEVER);
    FORCE_BRACES_MAP.put(CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE, VALUE_MULTILINE);
    FORCE_BRACES_MAP.put(CommonCodeStyleSettings.FORCE_BRACES_ALWAYS, VALUE_ALWAYS);
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

  @NotNull
  @Override
  public List<String> getChoices() {
    return ALL_VALUES;
  }
}
