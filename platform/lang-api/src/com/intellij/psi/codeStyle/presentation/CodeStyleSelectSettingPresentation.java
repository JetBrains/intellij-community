// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.presentation;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Shein
 */
public class CodeStyleSelectSettingPresentation extends CodeStyleSettingPresentation {

  protected int @NotNull [] myValues;
  protected String @NotNull [] myValueUiNames;

  protected int myLowerBound;
  protected int myUpperBound;

  public CodeStyleSelectSettingPresentation(@NotNull String fieldName, @NlsContexts.Label @NotNull String uiName,
                                            int @NotNull [] values, String @NotNull [] valueUiNames) {
    super(fieldName, uiName);

    assert(values.length == valueUiNames.length);
    assert(values.length > 0);

    myValues = values;
    myValueUiNames = valueUiNames;

    //TODO get bounds more gracefully
    myLowerBound = values[0];
    myUpperBound = values[0];
    for (int value : values) {
      myLowerBound = Math.min(value, myLowerBound);
      myUpperBound = Math.max(value, myUpperBound);
    }
  }

  @Override
  @NotNull
  public String getValueUiName(@NotNull Object value) {
    if (value instanceof Integer) {
      int intValue = (Integer) value;
      for (int i = 0; i < myValues.length; ++i) {
        if (myValues[i] == intValue) return myValueUiNames[i];
      }
    }
    return super.getValueUiName(value);
  }

  public int getLowerBound() {
    return myLowerBound;
  }

  public int getUpperBound() {
    return myUpperBound;
  }

  public int @NotNull [] getValues() {
    return myValues;
  }

  public String @NotNull [] getOptions() {
    return myValueUiNames;
  }
}
