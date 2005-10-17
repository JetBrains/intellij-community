package com.intellij.compiler.options;

import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2004
 */
public class ComparingUtils {
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
