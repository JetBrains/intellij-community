// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.presentation;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

public class CodeStyleBoundedIntegerSettingPresentation extends CodeStyleSettingPresentation {

  protected int myLowerBound;
  protected int myUpperBound;
  protected int myDefaultValue;
  protected String myDefaultValueUiName;

  public CodeStyleBoundedIntegerSettingPresentation(@NotNull String fieldName,
                                                    @NlsContexts.Label @NotNull String uiName,
                                                    int lowerBound,
                                                    int upperBound,
                                                    int defaultValue,
                                                    String defaultValueUiName) {
    super(fieldName, uiName);
    myLowerBound = lowerBound;
    myUpperBound = upperBound;
    myDefaultValue = defaultValue;
    myDefaultValueUiName = defaultValueUiName;
  }

  public int getLowerBound() {
    return myLowerBound;
  }

  public int getUpperBound() {
    return myUpperBound;
  }

  public int getDefaultValue() {
    return myDefaultValue;
  }

  @Override
  public @NotNull String getValueUiName(@NotNull Object value) {
    if (value instanceof Integer) {
      int intValue = (Integer) value;
      return intValue == myDefaultValue ? myDefaultValueUiName : super.getValueUiName(value);
    } else {
      return super.getValueUiName(value);
    }
  }
}
