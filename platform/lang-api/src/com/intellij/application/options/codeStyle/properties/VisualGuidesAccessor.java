// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class VisualGuidesAccessor extends CodeStylePropertyAccessor<List<Integer>> implements CodeStyleValueList {
  private final CodeStyleSettings mySettings;
  @Nullable private final Language myLanguage;

  public static final String VISUAL_GUIDES_PROPERTY_NAME = "visual_guides";

  VisualGuidesAccessor(@NotNull CodeStyleSettings settings, @Nullable Language language)
  {
    mySettings = settings;
    myLanguage = language;
  }

  @Override
  public boolean set(@NotNull List<Integer> extVal) {
    if (myLanguage != null) {
      mySettings.setSoftMargins(myLanguage, extVal);
    }
    else {
      mySettings.setDefaultSoftMargins(extVal);
    }
    return true;
  }

  @Override
  @Nullable
  public List<Integer> get() {
    return myLanguage != null ?
         mySettings.getCommonSettings(myLanguage).getSoftMargins() :
         mySettings.getDefaultSoftMargins();
  }

  @Override
  protected List<Integer> parseString(@NotNull String string) {
    return CodeStylePropertiesUtil.getValueList(string).stream()
      .map(s -> safeToInt(s))
      .filter(integer -> integer >= 0)
      .collect(Collectors.toList());
  }

  @Nullable
  @Override
  protected String valueToString(@NotNull List<Integer> value) {
    return CodeStylePropertiesUtil.toCommaSeparatedString(value);
  }

  @Override
  public boolean isEmptyListAllowed() {
    return true;
  }

  private static int safeToInt(@NotNull String s) {
    try {
      return Integer.parseInt(s);
    }
    catch (NumberFormatException nfe) {
      return -1;
    }
  }

  @Override
  public String getPropertyName() {
    return VISUAL_GUIDES_PROPERTY_NAME;
  }
}
