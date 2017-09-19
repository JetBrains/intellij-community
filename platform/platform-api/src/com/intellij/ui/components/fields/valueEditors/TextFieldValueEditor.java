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

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

public abstract class TextFieldValueEditor<T> extends AbstractValueEditor<T> {
  private JTextField myField;

  public TextFieldValueEditor(@NotNull JTextField field,
                              @Nullable String valueName,
                              @NotNull T defaultValue) {
    super(valueName, defaultValue);
    myField = field;
    myField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
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
      parseValue(text);
      return null;
    }
    catch (InvalidDataException ex) {
      return ex.getMessage();
    }
  }

  private void highlightState(boolean isValid) {
    myField.putClientProperty("JComponent.error.outline", isValid ? null : true);
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
