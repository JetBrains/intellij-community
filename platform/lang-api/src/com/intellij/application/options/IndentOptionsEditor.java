package com.intellij.application.options;

import com.intellij.ui.OptionGroup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;

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

  public void reset(final CodeStyleSettings settings, CodeStyleSettings.IndentOptions options) {
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
