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
import com.intellij.ui.components.fields.valueEditors.CommaSeparatedIntegersValueEditor;
import com.intellij.ui.components.fields.valueEditors.ValueEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A validating text component for comma-separated integer values. Extra spaces before and/or after comma are ignored.
 */
public class CommaSeparatedIntegersField extends JBTextField {

  private final ValueEditor<List<Integer>> myValueEditor;

  @SuppressWarnings("unused") // Default constructor
  public CommaSeparatedIntegersField() {
    this(null, Integer.MIN_VALUE, Integer.MAX_VALUE, null);
  }

  public CommaSeparatedIntegersField(@Nullable String valueName, int minValue, int maxValue, @Nullable String optionalText) {
    myValueEditor = new CommaSeparatedIntegersValueEditor(this, valueName, minValue, maxValue);
    if (optionalText != null) {
      getEmptyText().setText(optionalText);
    }
  }

  public void setValue(@NotNull List<Integer> newValue) {
    myValueEditor.setValue(newValue);
  }

  @NotNull
  public List<Integer> getValue() {
    return myValueEditor.getValue();
  }

  public void setDefaultValue(@NotNull List<Integer> defaultValue) {
    myValueEditor.setDefaultValue(defaultValue);
  }

  @NotNull
  public List<Integer> getDefaultValue() {
    return myValueEditor.getDefaultValue();
  }

  @Nullable
  public String getValueName() {
    return myValueEditor.getValueName();
  }

  public void validateContent() throws ConfigurationException {
    myValueEditor.validateContent();
  }

  public boolean isEmpty() {
    return getValue().isEmpty();
  }

  public void clear() {
    setValue(Collections.emptyList());
  }

  public ValueEditor<List<Integer>> getValueEditor() {
    return myValueEditor;
  }
}
