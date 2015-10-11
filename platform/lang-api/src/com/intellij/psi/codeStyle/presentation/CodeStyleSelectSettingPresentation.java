/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle.presentation;

import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Shein
 * @since 16.09.2015.
 */
public class CodeStyleSelectSettingPresentation extends CodeStyleSettingPresentation {

  @NotNull
  protected int[] myValues;
  @NotNull
  protected String[] myValueUiNames;

  protected int myLowerBound;
  protected int myUpperBound;

  public CodeStyleSelectSettingPresentation(@NotNull String fieldName, @NotNull String uiName,
                                            @NotNull int[] values, @NotNull String[] valueUiNames) {
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
      return super.getValueUiName(value);
    } else {
      return super.getValueUiName(value);
    }
  }

  public int getLowerBound() {
    return myLowerBound;
  }

  public int getUpperBound() {
    return myUpperBound;
  }

  @NotNull
  public int[] getValues() {
    return myValues;
  }

  @NotNull
  public String[] getOptions() {
    return myValueUiNames;
  }
}
