package com.intellij.application.options;

import com.intellij.ui.OptionGroup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class IndentOptionsEditor extends OptionGroup {
  public static final int LIST_CONT_INDENT = 1;
  public static final int LIST_LABEL_INDENT = 2;
  public static final int LIST_SMART_TABS = 4;
  private final int myListFlags;

  private boolean isListSmartTabs() {
    return (myListFlags & LIST_SMART_TABS) != 0;
  }

  private boolean isListContIndent() {
    return (myListFlags & LIST_CONT_INDENT) != 0;
  }

  private boolean isListLabelIndent() {
    return (myListFlags & LIST_LABEL_INDENT) != 0;
  }

  private JTextField myIndentField;
  private JTextField myContinuationIndentField;
  private JCheckBox myCbUseTab;
  private JCheckBox myCbSmartTabs;
  private JTextField myTabSizeField;
  private JLabel myTabSizeLabel;
  private JLabel myIndentLabel;
  private JLabel myContinuationIndentLabel;

  private JTextField myLabelIndent;
  private JLabel myLabelIndentLabel;

  private JCheckBox myLabelIndentAbsolute;
  private JCheckBox myCbDontIndentTopLevelMembers;

  public IndentOptionsEditor(int listFlags) {
    myListFlags = listFlags;
  }

  public JPanel createPanel() {
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

    if (isListLabelIndent()) {
      myLabelIndent = new JTextField(4);
      add(myLabelIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.label.indent")), myLabelIndent);

      myLabelIndentAbsolute = new JCheckBox(ApplicationBundle.message("checkbox.indent.absolute.label.indent"));
      add(myLabelIndentAbsolute, true);

      myCbDontIndentTopLevelMembers = new JCheckBox(ApplicationBundle.message("checkbox.do.not.indent.top.level.class.members"));
      add(myCbDontIndentTopLevelMembers);
    }

    final JPanel result = super.createPanel();
    result.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    return result;
  }

  private boolean isModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private boolean isModified(JTextField textField, int value) {
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
    isModified = isModified(myTabSizeField, options.TAB_SIZE);
    isModified |= isModified(myCbUseTab, options.USE_TAB_CHARACTER);
    isModified |= isModified(myIndentField, options.INDENT_SIZE);

    if (isListSmartTabs()) {
      isModified |= isModified(myCbSmartTabs, options.SMART_TABS);
    }

    if (isListContIndent()) {
      isModified |= isModified(myContinuationIndentField, options.CONTINUATION_INDENT_SIZE);
    }

    if (isListLabelIndent()) {
      isModified |= isModified(myLabelIndent, options.LABEL_INDENT_SIZE);
      isModified |= isModified(myLabelIndentAbsolute, options.LABEL_INDENT_ABSOLUTE);
      isModified |= isModified(myCbDontIndentTopLevelMembers, settings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS);
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

    if (isListLabelIndent()) {
      try {
        options.LABEL_INDENT_SIZE = Integer.parseInt(myLabelIndent.getText());
      }
      catch (NumberFormatException e) {
        //stay with default
      }
      options.LABEL_INDENT_ABSOLUTE = myLabelIndentAbsolute.isSelected();
      settings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = myCbDontIndentTopLevelMembers.isSelected();
    }
  }

  public void reset(final CodeStyleSettings settings, CodeStyleSettings.IndentOptions options) {
    myTabSizeField.setText(String.valueOf(options.TAB_SIZE));
    myCbUseTab.setSelected(options.USE_TAB_CHARACTER);

    myIndentField.setText(String.valueOf(options.INDENT_SIZE));
    if (isListContIndent()) myContinuationIndentField.setText(String.valueOf(options.CONTINUATION_INDENT_SIZE));
    if (isListLabelIndent()) {
      myLabelIndent.setText(Integer.toString(options.LABEL_INDENT_SIZE));
      myLabelIndentAbsolute.setSelected(options.LABEL_INDENT_ABSOLUTE);
      myCbDontIndentTopLevelMembers.setSelected(settings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS);
    }
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
    if (isListLabelIndent()) {
      myContinuationIndentField.setEnabled(enabled);
      myContinuationIndentLabel.setEnabled(enabled);
    }
    if (isListLabelIndent()) {
      myLabelIndent.setEnabled(enabled);
      myLabelIndentLabel.setEnabled(enabled);
      myLabelIndentAbsolute.setEnabled(enabled);
    }
  }

  private boolean isSmartTabValid(int indent, int tabSize) {
    return (indent / tabSize) * tabSize == indent;
  }
}
