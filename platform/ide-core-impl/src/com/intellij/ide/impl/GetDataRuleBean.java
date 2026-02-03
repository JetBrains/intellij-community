// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.KeyedLazyInstanceEP;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class GetDataRuleBean extends KeyedLazyInstanceEP<GetDataRule> {
  @Attribute(value = "type", converter = TypeConverter.class)
  public GetDataRuleType type = GetDataRuleType.PROVIDER;
  @Attribute("injectedContext")
  public boolean injectedContext;

  static final class TypeConverter extends Converter<GetDataRuleType> {
    @Override
    public @NotNull GetDataRuleType fromString(@NotNull String value) {
      return GetDataRuleType.valueOf(StringUtil.toUpperCase(value));
    }

    @Override
    public @NotNull String toString(@NotNull GetDataRuleType value) {
      return value.name();
    }
  }
}
