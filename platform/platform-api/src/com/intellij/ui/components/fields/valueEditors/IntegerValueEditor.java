// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.fields.valueEditors;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class IntegerValueEditor extends TextFieldValueEditor<Integer> {
  private int myMinValue;
  private int myMaxValue;
  private boolean myCanBeEmpty;

  public IntegerValueEditor(@NotNull JTextField field, @Nullable String valueName, @NotNull Integer defaultValue) {
    super(field, valueName, defaultValue);
  }

  @NotNull
  @Override
  public Integer parseValue(@Nullable String text) throws InvalidDataException {
    try {
      if (StringUtil.isEmpty(text)) {
        if (!myCanBeEmpty) {
          throw new InvalidDataException(ApplicationBundle.message("integer.field.value.expected"));
        }
        return getDefaultValue();
      }
      int value = Integer.parseInt(text);
      if (value < myMinValue || value > myMaxValue) {
        throw new InvalidDataException((ApplicationBundle.message("integer.field.value.out.of.range", value, myMinValue, myMaxValue)));
      }
      return value;
    }
    catch (NumberFormatException nfe) {
      throw new InvalidDataException((ApplicationBundle.message("integer.field.value.not.a.number", text)));
    }
  }

  @Override
  public String valueToString(@NotNull Integer value) {
    if (myCanBeEmpty && value.equals(getDefaultValue())) {
      return "";
    }
    return String.valueOf(value);
  }

  @Override
  public boolean isValid(@NotNull Integer value) {
    return value >= myMinValue && value <= myMaxValue;
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

  public boolean isCanBeEmpty() {
    return myCanBeEmpty;
  }

  public void setCanBeEmpty(boolean canBeEmpty) {
    myCanBeEmpty = canBeEmpty;
  }
}
