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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ValueEditor<T> {
  /**
   * Update an implementing component with the new value.
   * @param newValue The value to set.
   */
  void setValue(@NotNull T newValue);

  /**
   * Get a current value from the component if possible.
   * @return The current value or the default one if component doesn't contain valid data.
   */
  @NotNull
  T getValue();

  /**
   * Set the default value.
   * @param defaultValue The new default value.
   */
  void setDefaultValue(@NotNull T defaultValue);

  /**
   * @return The current default value.
   */
  @NotNull
  T getDefaultValue();

  /**
   * @return The value name used in validation messages.
   */
  @Nullable
  String getValueName();

  /**
   * Check if the current component content is valid and throw ConfigurationException if not.
   * @throws ConfigurationException The configuration exception.
   * @see Configurable#apply()
   */
  void validateContent() throws ConfigurationException;

  String getValueText();

  void setValueText(@NotNull String text);

  /**
   * Try parsing the text and convert it to the object of type T. Throw InvalidDataException if parsing fails.
   * @param text The text to parse.
   * @return Parsed data.
   * @throws com.intellij.openapi.util.InvalidDataException if parsing fails.
   */
  @NotNull
  T parseValue(@Nullable String text);

  /**
   * Convert the value to an equivalent text string.
   * @param value The value convert.
   * @return The resulting string (the same value should be returned when the string is converted back with {@link #parseValue} method).
   */
  String valueToString(@NotNull T value);

  /**
   * Check the the given value is valid. For example, an integer number is within an expected range and so on.
   * @param value The value to check.
   */
  boolean isValid(@NotNull T value);

  void addListener(@NotNull Listener<T> editorListener);

  interface Listener<T> {
    void valueChanged(@NotNull T newValue);
  }
}
