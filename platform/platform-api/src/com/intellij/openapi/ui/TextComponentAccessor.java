/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.TextFieldWithStoredHistory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Text component accessor. It wraps access to the content of text component
 * and it might perform some translations between text representation and
 * component objects.
 *
 * @author dyoma
 */
public interface TextComponentAccessor<T extends Component> {
  /**
   * The accessor that gets and changes whole text
   */
  TextComponentAccessor<JTextField> TEXT_FIELD_WHOLE_TEXT = new TextComponentAccessor<JTextField>() {
    @Override
    public String getText(JTextField textField) {
      return textField.getText();
    }

    @Override
    public void setText(JTextField textField, @NotNull String text) {
      textField.setText(text);
    }
  };

  /**
   * The accessor that replaces selection or whole text if there is no selection
   */
  TextComponentAccessor<JTextField> TEXT_FIELD_SELECTED_TEXT = new TextComponentAccessor<JTextField>() {
    @Override
    public String getText(JTextField textField) {
      String selectedText = textField.getSelectedText();
      return selectedText != null ? selectedText : textField.getText();
    }

    @Override
    public void setText(JTextField textField, @NotNull String text) {
      if (textField.getSelectedText() != null) textField.replaceSelection(text);
      else textField.setText(text);
    }
  };

  /**
   * The accessor that gets and changes whole text
   */
  TextComponentAccessor<JComboBox> STRING_COMBOBOX_WHOLE_TEXT = new TextComponentAccessor<JComboBox>() {
    @Override
    public String getText(JComboBox comboBox) {
      Object item = comboBox.getEditor().getItem();
      return item.toString();
    }

    @Override
    public void setText(JComboBox comboBox, @NotNull String text) {
      comboBox.getEditor().setItem(text);
    }
  };
  /**
   * The accessor that gets and changes whole text
   */
  TextComponentAccessor<TextFieldWithHistory> TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT = new TextComponentAccessor<TextFieldWithHistory>() {
    @Override
    public String getText(TextFieldWithHistory textField) {
      return textField.getText();
    }

    @Override
    public void setText(TextFieldWithHistory textField, @NotNull String text) {
      textField.setText(text);
    }
  };

  /**
   * The accessor that gets and changes whole text
   */
  TextComponentAccessor<TextFieldWithStoredHistory> TEXT_FIELD_WITH_STORED_HISTORY_WHOLE_TEXT = new TextComponentAccessor<TextFieldWithStoredHistory>() {
    @Override
    public String getText(TextFieldWithStoredHistory textField) {
      return textField.getText();
    }

    @Override
    public void setText(TextFieldWithStoredHistory textField, @NotNull String text) {
      textField.setText(text);
    }
  };

  /**
   * Get text from component
   * @param component a component to examine
   * @return the text (possibly adjusted)
   */
  String getText(T component);

  /**
   * Set text to the component
   * @param component the component
   * @param text the text to set
   */
  void setText(T component, @NotNull String text);
}
