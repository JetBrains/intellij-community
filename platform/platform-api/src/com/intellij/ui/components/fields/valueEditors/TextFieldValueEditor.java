// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.fields.valueEditors;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

public abstract class TextFieldValueEditor<T> extends AbstractValueEditor<T> {
  private final JTextField myField;

  public TextFieldValueEditor(@NotNull JTextField field,
                              @Nullable String valueName,
                              @NotNull T defaultValue) {
    super(valueName, defaultValue);
    myField = field;
    myField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        String errorText = validateTextOnChange(myField.getText(), e);
        highlightState(StringUtil.isEmpty(errorText));
        if (StringUtil.isNotEmpty(errorText)) {
          setErrorText(errorText);
        }
      }
    });
  }

  @SuppressWarnings("unused")
  protected String validateTextOnChange(String text, DocumentEvent e) {
    try {
      T newValue = parseValue(text);
      fireValueChanged(newValue);
      return null;
    }
    catch (InvalidDataException ex) {
      return ex.getMessage();
    }
  }

  private void highlightState(boolean isValid) {
    myField.putClientProperty("JComponent.outline", isValid ? null : "error");
  }

  @SuppressWarnings("unused")
  protected void setErrorText(@NotNull String errorText) {
    // TODO: to be implemented later
  }


  @Override
  public String getValueText() {
    return myField.getText();
  }

  @Override
  public void setValueText(@NotNull String text) {
    myField.setText(text);
  }

}
