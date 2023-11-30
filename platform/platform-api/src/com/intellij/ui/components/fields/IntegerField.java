// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.fields;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
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

  public @NotNull Integer getValue() {
    return myValueEditor.getValue();
  }

  public void setValue(@NotNull Integer newValue) {
    myValueEditor.setValue(newValue);
  }

  public void setValueName(@Nullable String valueName) {
    myValueEditor.setValueName(valueName);
  }

  public @Nullable String getValueName() {
    return myValueEditor.getValueName();
  }

  public void validateContent() throws ConfigurationException {
    myValueEditor.validateContent();
  }

  public void setDefaultValueText(@NotNull @NlsContexts.StatusText String text) {
    getEmptyText().setText(text);
  }

  public void setDefaultValue(@NotNull Integer defaultValue) {
    myValueEditor.setDefaultValue(defaultValue);
  }

  public @NotNull Integer getDefaultValue() {
    return myValueEditor.getDefaultValue();
  }

  public void resetToDefault() {
    myValueEditor.setValue(myValueEditor.getDefaultValue());
  }

  public IntegerValueEditor getValueEditor() {
    return myValueEditor;
  }
}
