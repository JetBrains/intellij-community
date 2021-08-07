// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.TextFieldWithStoredHistory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class TextComponentAccessors {
  /**
   * The accessor that gets and changes whole text
   */
  public static final TextComponentAccessor<TextFieldWithHistory> TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT = new TextComponentAccessor<>() {
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
  public static final TextComponentAccessor<TextFieldWithStoredHistory> TEXT_FIELD_WITH_STORED_HISTORY_WHOLE_TEXT = new TextComponentAccessor<>() {
    @Override
    public String getText(TextFieldWithStoredHistory textField) {
      return textField.getText();
    }

    @Override
    public void setText(TextFieldWithStoredHistory textField, @NotNull String text) {
      textField.setText(text);
    }
  };

  private TextComponentAccessors() {
  }
}
