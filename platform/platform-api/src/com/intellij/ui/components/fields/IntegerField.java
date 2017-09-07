/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.components.fields;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntegerField extends AbstractValueInputField<Integer> {

  private int myMinValue;
  private int myMaxValue;

  public IntegerField() {
    this(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  public IntegerField(int minValue, int maxValue) {
    super(minValue);
    myMinValue = minValue;
    myMaxValue = maxValue;
  }

  public int getMinValue() {
    return myMinValue;
  }

  public int getMaxValue() {
    return myMaxValue;
  }

  public void setMinValue(int minValue) {
    myMinValue = minValue;
  }

  public void setMaxValue(int maxValue) {
    myMaxValue = maxValue;
  }

  @NotNull
  @Override
  protected ParseResult parseValue(@Nullable String text) {
    try {
      if (StringUtil.isEmpty(text)) {
        return new ParseResult(ApplicationBundle.message("integer.field.value.expected"));
      }
      int value = Integer.parseInt(text);
      if (value < myMinValue || value > myMaxValue) {
        return new ParseResult(ApplicationBundle.message("integer.field.value.out.of.range", value, myMinValue, myMaxValue));
      }
      return new ParseResult(value);
    }
    catch (NumberFormatException nfe) {
      return new ParseResult(ApplicationBundle.message("integer.field.value.not.a.number", text));
    }
  }

  @Override
  protected String valueToString(@NotNull Integer value) {
    return String.valueOf(value);
  }

  @Override
  protected void assertValid(@NotNull Integer value) {
    assert value >= myMinValue && value <= myMaxValue : "Value is out of range " + myMinValue + ".." + myMaxValue;
  }

}
