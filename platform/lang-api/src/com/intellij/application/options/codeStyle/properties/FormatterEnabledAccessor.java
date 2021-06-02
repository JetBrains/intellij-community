// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.formatting.fileSet.AnyFileDescriptor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.ExcludedFiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class FormatterEnabledAccessor extends CodeStylePropertyAccessor<Boolean> implements CodeStyleChoiceList {
  public static final String PROPERTY_NAME = "formatter_enabled";

  private final CodeStyleSettings mySettings;

  public FormatterEnabledAccessor(CodeStyleSettings settings) {
    mySettings = settings;
  }

  @Override
  public @NotNull List<String> getChoices() {
    return BooleanAccessor.BOOLEAN_VALS;
  }

  @Override
  public boolean set(@NotNull Boolean extVal) {
    ExcludedFiles excludedFiles = mySettings.getExcludedFiles();
    if (extVal) {
      excludedFiles.clear();
    }
    else {
      excludedFiles.setDescriptors(Collections.singletonList(new AnyFileDescriptor()));
    }
    return true;
  }

  @Override
  public @Nullable Boolean get() {
    return null;
  }

  @Override
  protected @Nullable Boolean parseString(@NotNull String string) {
    return "true".equalsIgnoreCase(string);
  }

  @Override
  protected @Nullable String valueToString(@NotNull Boolean value) {
    return value ? "true" : null;
  }

  @Override
  public String getPropertyName() {
    return PROPERTY_NAME;
  }
}
