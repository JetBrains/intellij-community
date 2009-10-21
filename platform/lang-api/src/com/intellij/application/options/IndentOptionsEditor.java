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

package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.OptionGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class IndentOptionsEditor extends OptionGroup {
  private JTextField myIndentField;
  protected JCheckBox myCbUseTab;
  private JTextField myTabSizeField;
  private JLabel myTabSizeLabel;
  private JLabel myIndentLabel;

  public JPanel createPanel() {
    addComponents();

    final JPanel result = super.createPanel();
    result.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    return result;
  }

  protected void addComponents() {
    addTabOptions();

    myTabSizeField = new JTextField(4);
    myTabSizeField.setMinimumSize(myTabSizeField.getPreferredSize());
    myTabSizeLabel = new JLabel(ApplicationBundle.message("editbox.indent.tab.size"));
    add(myTabSizeLabel, myTabSizeField);

    myIndentField = new JTextField(4);
    myIndentField.setMinimumSize(myTabSizeField.getPreferredSize());
    myIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.indent"));
    add(myIndentLabel, myIndentField);
  }

  protected void addTabOptions() {
    myCbUseTab = new JCheckBox(ApplicationBundle.message("checkbox.indent.use.tab.character"));
    add(myCbUseTab);
  }

  protected static boolean isFieldModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  protected static boolean isFieldModified(JTextField textField, int value) {
    try {
      int fieldValue = Integer.parseInt(textField.getText().trim());
      return fieldValue != value;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }

  public boolean isModified(final CodeStyleSettings settings, CodeStyleSettings.IndentOptions options) {
    boolean isModified;
    isModified = isFieldModified(myTabSizeField, options.TAB_SIZE);
    isModified |= isFieldModified(myCbUseTab, options.USE_TAB_CHARACTER);
    isModified |= isFieldModified(myIndentField, options.INDENT_SIZE);

    return isModified;
  }

  protected int getUIIndent() {
    final String indentText = myIndentField.getText();
    try {
      return Math.max(Integer.parseInt(indentText), 1);
    }
    catch (NumberFormatException e) {
      //stay with default
    }

    return 4;
  }

  protected int getUITabSize() {
    try {
      return Math.max(Integer.parseInt(myTabSizeField.getText()), 1);
    }
    catch (NumberFormatException e) {
      //stay with default
    }

    return 4;
  }

  public void apply(final CodeStyleSettings settings, CodeStyleSettings.IndentOptions options) {
    options.INDENT_SIZE = getUIIndent();
    options.TAB_SIZE = getUITabSize();
    options.USE_TAB_CHARACTER = myCbUseTab.isSelected();
  }

  public void reset(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings.IndentOptions options) {
    myTabSizeField.setText(String.valueOf(options.TAB_SIZE));
    myCbUseTab.setSelected(options.USE_TAB_CHARACTER);

    myIndentField.setText(String.valueOf(options.INDENT_SIZE));
  }

  public void setEnabled(boolean enabled) {
    myIndentField.setEnabled(enabled);
    myIndentLabel.setEnabled(enabled);
    myTabSizeField.setEnabled(enabled);
    myTabSizeLabel.setEnabled(enabled);
    myCbUseTab.setEnabled(enabled);
  }
}
