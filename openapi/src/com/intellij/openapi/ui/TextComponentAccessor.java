/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui;

import com.intellij.ui.TextFieldWithHistory;

import javax.swing.*;
import java.awt.*;

/**
 * @author dyoma
 */
public interface TextComponentAccessor<T extends Component> {
  TextComponentAccessor<JTextField> TEXT_FIELD_WHOLE_TEXT = new TextComponentAccessor<JTextField>() {
    public String getText(JTextField textField) {
      return textField.getText();
    }

    public void setText(JTextField textField, String text) {
      textField.setText(text);
    }
  };

  TextComponentAccessor<JTextField> TEXT_FIELD_SELECTED_TEXT = new TextComponentAccessor<JTextField>() {
    public String getText(JTextField textField) {
      String selectedText = textField.getSelectedText();
      return selectedText != null ? selectedText : textField.getText();
    }

    public void setText(JTextField textField, String text) {
      if (textField.getSelectedText() != null) textField.replaceSelection(text);
      else textField.setText(text);
    }
  };

  TextComponentAccessor<JComboBox> STRING_COMBOBOX_WHOLE_TEXT = new TextComponentAccessor<JComboBox>() {
    public String getText(JComboBox comboBox) {
      Object item = comboBox.getEditor().getItem();
      return item.toString();
    }

    public void setText(JComboBox comboBox, String text) {
      comboBox.getEditor().setItem(text);
    }
  };

  TextComponentAccessor<TextFieldWithHistory> TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT = new TextComponentAccessor<TextFieldWithHistory>() {
    public String getText(TextFieldWithHistory textField) {
      return textField.getText();
    }

    public void setText(TextFieldWithHistory textField, String text) {
      textField.setText(text);
    }
  };

  String getText(T component);

  void setText(T component, String text);
}
