// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.options;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public final class ComparingUtils {
  public static boolean isModified(TextFieldWithBrowseButton field, String value) {
    return !field.getText().equals(value);
  }

  public static boolean isModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  public static boolean isModified(JTextField textField, int value) {
    try {
      int fieldValue = Integer.parseInt(textField.getText().trim());
      return fieldValue != value;
    }
    catch(NumberFormatException e) {
      return false;
    }
  }

  public static boolean isModified(RawCommandLineEditor editor, String value) {
    return !editor.getText().equals(value);
  }

  public static boolean isModified(JTextField textField, String value) {
    return !textField.getText().equals(value);
  }
}
