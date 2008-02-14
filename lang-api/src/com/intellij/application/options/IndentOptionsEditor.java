package com.intellij.application.options;

import com.intellij.ui.OptionGroup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class IndentOptionsEditor extends OptionGroup {
  public static final int LIST_CONT_INDENT = 1;
  public static final int LIST_SMART_TABS = 4;
  private final int myListFlags;

  private boolean isListSmartTabs() {
    return (myListFlags & LIST_SMART_TABS) != 0;
  }

  private boolean isListContIndent() {
    return (myListFlags & LIST_CONT_INDENT) != 0;
  }

  private JTextField myIndentField;
  private JTextField myContinuationIndentField;
  private JCheckBox myCbUseTab;
  private JCheckBox myCbSmartTabs;
  private JTextField myTabSizeField;
  private JLabel myTabSizeLabel;
  private JLabel myIndentLabel;
  private JLabel myContinuationIndentLabel;

  public IndentOptionsEditor(int listFlags) {
    myListFlags = listFlags;
  }

  public JPanel createPanel() {
    addComponents();

    final JPanel result = super.createPanel();
    result.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    return result;
  }

  protected void addComponents() {
    myCbUseTab = new JCheckBox(ApplicationBundle.message("checkbox.indent.use.tab.character"));
    add(myCbUseTab);

    if (isListSmartTabs()) {
      myCbSmartTabs = new JCheckBox(ApplicationBundle.message("checkbox.indent.smart.tabs"));
      add(myCbSmartTabs, true);
    }

    myTabSizeField = new JTextField(4);
    myTabSizeField.setMinimumSize(myTabSizeField.getPreferredSize());
    myTabSizeLabel = new JLabel(ApplicationBundle.message("editbox.indent.tab.size"));
    add(myTabSizeLabel, myTabSizeField);

    myIndentField = new JTextField(4);
    myIndentField.setMinimumSize(myTabSizeField.getPreferredSize());
    myIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.indent"));
    add(myIndentLabel, myIndentField);

    if (isListContIndent()) {
      myContinuationIndentField = new JTextField(4);
      myContinuationIndentField.setMinimumSize(myContinuationIndentField.getPreferredSize());
      myContinuationIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.continuation.indent"));
      add(myContinuationIndentLabel, myContinuationIndentField);
    }
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

    if (isListSmartTabs()) {
      isModified |= isFieldModified(myCbSmartTabs, options.SMART_TABS);
    }

    if (isListContIndent()) {
      isModified |= isFieldModified(myContinuationIndentField, options.CONTINUATION_INDENT_SIZE);
    }

    return isModified;
  }

  private int getUIIndent() {
    try {
      return Math.max(Integer.parseInt(myIndentField.getText()), 1);
    }
    catch (NumberFormatException e) {
      //stay with default
    }

    return 4;
  }

  private int getUITabSize() {
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

    if (isListContIndent()) {
      try {
        options.CONTINUATION_INDENT_SIZE = Math.max(Integer.parseInt(myContinuationIndentField.getText()), 0);
      }
      catch (NumberFormatException e) {
        //stay with default
      }
    }

    if (isListSmartTabs()) {
      options.SMART_TABS = isSmartTabValid(options.INDENT_SIZE, options.TAB_SIZE) && myCbSmartTabs.isSelected();
    }
  }

  public void reset(final CodeStyleSettings settings, CodeStyleSettings.IndentOptions options) {
    myTabSizeField.setText(String.valueOf(options.TAB_SIZE));
    myCbUseTab.setSelected(options.USE_TAB_CHARACTER);

    myIndentField.setText(String.valueOf(options.INDENT_SIZE));
    if (isListContIndent()) myContinuationIndentField.setText(String.valueOf(options.CONTINUATION_INDENT_SIZE));
    if (isListSmartTabs()) myCbSmartTabs.setSelected(options.SMART_TABS);
  }

  public void setEnabled(boolean enabled) {
    myIndentField.setEnabled(enabled);
    myIndentLabel.setEnabled(enabled);
    myTabSizeField.setEnabled(enabled);
    myTabSizeLabel.setEnabled(enabled);
    myCbUseTab.setEnabled(enabled);
    if (isListSmartTabs()) {
      boolean smartTabsChecked = enabled && myCbUseTab.isSelected();
      boolean smartTabsValid = smartTabsChecked && isSmartTabValid(getUIIndent(), getUITabSize());
      myCbSmartTabs.setEnabled(smartTabsValid);
      myCbSmartTabs.setToolTipText(
        smartTabsChecked && !smartTabsValid ? ApplicationBundle.message("tooltip.indent.must.be.multiple.of.tab.size.for.smart.tabs.to.operate") : null);
    }
    if (isListContIndent()) {
      myContinuationIndentField.setEnabled(enabled);
      myContinuationIndentLabel.setEnabled(enabled);
    }
  }

  private boolean isSmartTabValid(int indent, int tabSize) {
    return (indent / tabSize) * tabSize == indent;
  }
}
