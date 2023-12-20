// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OverrideLanguageIndentOptionsAccessor extends CodeStylePropertyAccessor<Boolean> {
  private final IndentOptions myOptions;
  public static final String OVERRIDE_LANGUAGE_INDENT_OPTIONS_PROPERTY_NAME = "OverrideLanguageOptions";

  OverrideLanguageIndentOptionsAccessor(@NotNull IndentOptions options) {
    myOptions = options;
  }

  @Override
  public boolean set(@NotNull Boolean extVal) {
    myOptions.setOverrideLanguageOptions(extVal);
    return true;
  }

  @Override
  public @Nullable Boolean get() {
    // Do not export this value
    return null;
  }

  @Override
  protected @Nullable Boolean parseString(@NotNull String string) {
    // Editor config has unified indentation across the whole file,
    // so this is always true regardless of number of tabs or
    // per language specific settings.
    return true;
  }

  @Override
  protected @Nullable String valueToString(@NotNull Boolean value) {
    // Do not export this value
    return null;
  }

  @Override
  public String getPropertyName() {
    return OVERRIDE_LANGUAGE_INDENT_OPTIONS_PROPERTY_NAME;
  }
}
