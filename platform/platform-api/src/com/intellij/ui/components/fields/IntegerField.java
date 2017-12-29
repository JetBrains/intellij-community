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

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.valueEditors.IntegerValueEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A validating text field to input integer numbers with minimum, maximum and default values.
 */
public class IntegerField extends JBTextField {

  private final IntegerValueEditor myValueEditor;

  public IntegerField() {
    this(null, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  public IntegerField(@Nullable String valueName, int minValue, int maxValue) {
    myValueEditor = new IntegerValueEditor(this, valueName, minValue);
    myValueEditor.setMinValue(minValue);
    myValueEditor.setMaxValue(maxValue);
  }

  public int getMinValue() {
    return myValueEditor.getMinValue();
  }

  public int getMaxValue() {
    return myValueEditor.getMaxValue();
  }

  public void setMinValue(int minValue) {
    myValueEditor.setMinValue(minValue);
  }

  public void setMaxValue(int maxValue) {
    myValueEditor.setMaxValue(maxValue);
  }

  @SuppressWarnings("unused") // Bean property
  public boolean isCanBeEmpty() {
    return myValueEditor.isCanBeEmpty();
  }

  public void setCanBeEmpty(boolean canBeEmpty) {
    myValueEditor.setCanBeEmpty(canBeEmpty);
  }

  @NotNull
  public Integer getValue() {
    return myValueEditor.getValue();
  }

  public void setValue(@NotNull Integer newValue) {
    myValueEditor.setValue(newValue);
  }

  public void setValueName(@Nullable String valueName) {
    myValueEditor.setValueName(valueName);
  }

  @Nullable
  public String getValueName() {
    return myValueEditor.getValueName();
  }

  public void validateContent() throws ConfigurationException {
    myValueEditor.validateContent();
  }

  public void setDefaultValueText(@NotNull String text) {
    getEmptyText().setText(text);
  }

  public void setDefaultValue(@NotNull Integer defaultValue) {
    myValueEditor.setDefaultValue(defaultValue);
  }

  @NotNull
  public Integer getDefaultValue() {
    return myValueEditor.getDefaultValue();
  }

  public void resetToDefault() {
    myValueEditor.setValue(myValueEditor.getDefaultValue());
  }

  public IntegerValueEditor getValueEditor() {
    return myValueEditor;
  }
}
