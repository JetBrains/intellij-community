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
package com.intellij.ui.components.fields.valueEditors;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.InvalidDataException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractValueEditor<T> implements ValueEditor<T> {

  private @NotNull T myDefaultValue;
  private @Nullable String myValueName;

  protected AbstractValueEditor(@Nullable String valueName,
                                @NotNull T defaultValue) {
    myDefaultValue = defaultValue;
    myValueName = valueName;
  }

  /**
   * Try parsing the text field value or return the default value if parsing fails.
   * @return The parsed value as returned by {@link #parseValue(String)} method or the default value.
   */
  @NotNull
  public T getValue() {
    try {
      return parseValue(getValueText());
    }
    catch (InvalidDataException ex) {
      return getDefaultValue();
    }
  }

  public void setValueName(@Nullable String valueName) {
    myValueName = valueName;
  }

  @Nullable
  @Override
  public String getValueName() {
    return myValueName;
  }

  /**
   * Check the given value and set it an equivalent text string.
   * @param newValue The new value to set. It must be either a default value or a value following the rules defined in
   * {@link #assertValid(Object)} method, otherwise the method will fail with assertion.
   */
  public void setValue(@NotNull T newValue) {
    if (!newValue.equals(getDefaultValue())) assertValid(newValue);
    setValueText(valueToString(newValue));
  }

  /**
   * Try parsing the current text using {@link #parseValue(String)} method and throw a configuration exception in case of a
   * failure.
   * @throws ConfigurationException If the text doesn't represent a valid value.
   */
  public void validateContent() throws ConfigurationException {
    try {
      parseValue(getValueText());
    }
    catch (InvalidDataException ex) {
      String name = getValueName();
      throw new ConfigurationException((name != null ? name + " " : "") + ex.getMessage());
    }
  }

  @Override
  public void setDefaultValue(@NotNull T defaultValue) {
    myDefaultValue = defaultValue;
  }

  @NotNull
  @Override
  public T getDefaultValue() {
    return myDefaultValue;
  }
}
