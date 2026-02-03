// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.fields.valueEditors;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.InvalidDataException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractValueEditor<T> implements ValueEditor<T> {

  private @NotNull T myDefaultValue;
  private @Nullable String myValueName;
  private final List<Listener<T>> myListeners = new ArrayList<>();

  protected AbstractValueEditor(@Nullable String valueName,
                                @NotNull T defaultValue) {
    myDefaultValue = defaultValue;
    myValueName = valueName;
  }

  /**
   * Try parsing the text field value or return the default value if parsing fails.
   * @return The parsed value as returned by {@link #parseValue(String)} method or the default value.
   */
  @Override
  public @NotNull T getValue() {
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

  @Override
  public @Nullable String getValueName() {
    return myValueName;
  }

  /**
   * Check the given value and set it an equivalent text string.
   * @param newValue The new value to set. It must be either a default value or a value following the rules defined in
   * {@link #isValid(Object)} method, otherwise the default value will be used.
   */
  @Override
  public void setValue(@NotNull T newValue) {
    if (!newValue.equals(getDefaultValue()) && !isValid(newValue)) {
      newValue = getDefaultValue();
    }
    setValueText(valueToString(newValue));
  }

  /**
   * Try parsing the current text using {@link #parseValue(String)} method and throw a configuration exception in case of a
   * failure.
   * @throws ConfigurationException If the text doesn't represent a valid value.
   */
  @Override
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

  @Override
  public @NotNull T getDefaultValue() {
    return myDefaultValue;
  }

  @Override
  public void addListener(@NotNull Listener<T> editorListener) {
    myListeners.add(editorListener);
  }

  public void fireValueChanged(@NotNull T newValue) {
    for (Listener<T> listener : myListeners) listener.valueChanged(newValue);
  }
}
