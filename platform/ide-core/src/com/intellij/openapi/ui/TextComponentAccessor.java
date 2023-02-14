// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.util.NlsSafe;
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
   * Get text from component
   * @param component a component to examine
   * @return the text (possibly adjusted)
   */
  @NlsSafe String getText(T component);

  /**
   * Set text to the component
   * @param component the component
   * @param text the text to set
   */
  void setText(T component, @NlsSafe @NotNull String text);

  /**
   * The accessor that replaces selection or whole text if there is no selection
   */
  TextComponentAccessor<JTextField> TEXT_FIELD_SELECTED_TEXT = new TextComponentAccessor<>() {
    @Override
    public String getText(JTextField textField) {
      String selectedText = textField.getSelectedText();
      return selectedText != null ? selectedText : textField.getText();
    }

    @Override
    public void setText(JTextField textField, @NotNull String text) {
      if (textField.getSelectedText() != null) {
        textField.replaceSelection(text);
      }
      else {
        textField.setText(text);
      }
    }
  };

  /**
   * The accessor that gets and changes whole text
   */
  TextComponentAccessor<JTextField> TEXT_FIELD_WHOLE_TEXT = new TextComponentAccessor<>() {
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
   * The accessor that gets and changes whole text
   */
  TextComponentAccessor<JComboBox> STRING_COMBOBOX_WHOLE_TEXT = new TextComponentAccessor<>() {
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
}
