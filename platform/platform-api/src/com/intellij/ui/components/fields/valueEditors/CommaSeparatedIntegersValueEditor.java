// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.fields.valueEditors;

import com.intellij.openapi.util.InvalidDataException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommaSeparatedIntegersValueEditor extends TextFieldValueEditor<List<Integer>> {
  private final int myMinValue;
  private final int myMaxValue;

  public CommaSeparatedIntegersValueEditor(@NotNull JTextField field,
                                           @Nullable String valueName, int minValue, int maxValue) {
    super(field, valueName, Collections.emptyList());
    myMinValue = minValue;
    myMaxValue = maxValue;
  }

  @NotNull
  @Override
  public List<Integer> parseValue(@Nullable String text) throws InvalidDataException {
    if (text == null || text.isEmpty()) return Collections.emptyList();
    String[] chunks = text.split("\\s*,\\s*");
    List<Integer> values = new ArrayList<>(chunks.length);
    for (String chunk : chunks) {
      try {
        int value = Integer.parseInt(chunk);
        if (value < myMinValue || value > myMaxValue) {
          throw new InvalidDataException("Value " + value + " is out of range " + myMinValue + ".." + myMaxValue);
        }
        values.add(value);
      }
      catch (NumberFormatException nfe) {
        throw new InvalidDataException("Value '" + chunk + "' is not an integer number");
      }
    }
    Collections.sort(values);
    return values;
  }

  @Override
  public String valueToString(@NotNull List<Integer> valueList) {
    return intListToString(valueList);
  }

  @Override
  public boolean isValid(@NotNull List<Integer> value) {
    return true;
  }

  public static String intListToString(@NotNull List<Integer> valueList) {
    StringBuilder sb = new StringBuilder();
    for (Integer value : valueList) {
      if (sb.length() != 0) sb.append(", ");
      sb.append(value);
    }
    return sb.toString();
  }
}
