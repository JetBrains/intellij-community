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
 * @since 15.09.2015.
 */
public class CodeStyleBoundedIntegerSettingPresentation extends CodeStyleSettingPresentation {

  protected int myLowerBound;
  protected int myUpperBound;
  protected int myDefaultValue;
  protected String myDefaultValueUiName;

  public CodeStyleBoundedIntegerSettingPresentation(@NotNull String fieldName,
                                                    @NotNull String uiName,
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
  @NotNull
  public String getValueUiName(@NotNull Object value) {
    if (value instanceof Integer) {
      int intValue = (Integer) value;
      return intValue == myDefaultValue ? myDefaultValueUiName : super.getValueUiName(value);
    } else {
      return super.getValueUiName(value);
    }
  }
}
