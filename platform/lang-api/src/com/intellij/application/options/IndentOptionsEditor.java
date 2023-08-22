// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.ui.OptionGroup;
import com.intellij.ui.components.fields.IntegerField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.psi.codeStyle.CodeStyleConstraints.*;
import static com.intellij.psi.codeStyle.CodeStyleDefaults.DEFAULT_INDENT_SIZE;
import static com.intellij.psi.codeStyle.CodeStyleDefaults.DEFAULT_TAB_SIZE;
import static com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType.INDENT_SETTINGS;

/**
 * A specialized option group for editing indent options.
 * <p>
 *   <em>Implementation note:</em> make sure all the necessary Swing components are only created
 *   in the {@link #createPanel()} and/or {@link #addComponents()} methods and not in the
 *   constructor or field/property initializers. See {@link OptionGroup} for details.
 * </p>
 */
public class IndentOptionsEditor extends OptionGroup implements CodeStyleSettingsCustomizable {

  private JTextField myIndentField;
  private JCheckBox myCbUseTab;
  private JTextField myTabSizeField;
  private JLabel myTabSizeLabel;
  private JLabel myIndentLabel;

  private final @Nullable LanguageCodeStyleSettingsProvider myProvider;

  public IndentOptionsEditor() {
    this(null);
  }

  /**
   * @param provider The provider which will be used to customize the indent options editor. If {@code null} is passed, no customization
   *                 will be carried out and thus all the available options will be shown.
   */
  public IndentOptionsEditor(@Nullable LanguageCodeStyleSettingsProvider provider) {
    myProvider = provider;
  }

  @Override
  public JPanel createPanel() {
    addComponents();
    if (myProvider != null) {
      myProvider.customizeSettings(this, INDENT_SETTINGS);
    }
    return super.createPanel();
  }

  protected void addComponents() {
    addTabOptions();
    addTabSizeField();
    addIndentField();
  }

  protected void addIndentField() {
    myIndentField = createIndentTextField(getIndentLabel(), MIN_INDENT_SIZE, MAX_INDENT_SIZE, DEFAULT_INDENT_SIZE);
    myIndentLabel = new JLabel(getIndentLabel());
    add(myIndentLabel, myIndentField);
  }

  protected void addTabSizeField() {
    myTabSizeField = createIndentTextField(getTabSizeLabel(), MIN_TAB_SIZE, MAX_TAB_SIZE, DEFAULT_TAB_SIZE);
    myTabSizeLabel = new JLabel(getTabSizeLabel());
    add(myTabSizeLabel, myTabSizeField);
  }

  protected IntegerField createIndentTextField(@Nullable String valueName, int minSize, int maxSize, int defaultValue) {
    IntegerField field = new IntegerField(valueName, minSize, maxSize);
    field.setDefaultValue(defaultValue);
    field.setColumns(4);
    if (defaultValue < 0) field.setCanBeEmpty(true);
    field.setMinimumSize(field.getPreferredSize());
    return field;
  }

  protected void addTabOptions() {
    myCbUseTab = new JCheckBox(ApplicationBundle.message("checkbox.indent.use.tab.character"));
    add(myCbUseTab);
  }

  @Override
  public void showAllStandardOptions() {
    setVisible(true);
  }

  @Override
  public void showStandardOptions(String... optionNames) {
    setVisible(false);
    for (String optionName : optionNames) {
      if (IndentOption.INDENT_SIZE.toString().equals(optionName)) {
        myIndentLabel.setVisible(true);
        myIndentField.setVisible(true);
      }
      else if (IndentOption.TAB_SIZE.toString().equals(optionName)) {
        myTabSizeField.setVisible(true);
        myTabSizeLabel.setVisible(true);
      }
      else if (IndentOption.USE_TAB_CHARACTER.toString().equals(optionName)) {
        myCbUseTab.setVisible(true);
      }
    }
  }



  protected static boolean isFieldModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  protected static boolean isFieldModified(JTextField textField, int value) {
    if (textField instanceof IntegerField) return ((IntegerField)textField).getValue() != value;
    try {
      int fieldValue = Integer.parseInt(textField.getText().trim());
      return fieldValue != value;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }

  public boolean isModified(final CodeStyleSettings settings, CommonCodeStyleSettings.IndentOptions options) {
    boolean isModified;
    isModified = isFieldModified(myTabSizeField, options.TAB_SIZE);
    isModified |= isFieldModified(myCbUseTab, options.USE_TAB_CHARACTER);
    isModified |= isFieldModified(myIndentField, options.INDENT_SIZE);

    return isModified;
  }

  protected int getUIIndent() {
    assert myIndentField instanceof IntegerField;
    return ((IntegerField)myIndentField).getValue();
  }

  protected int getUITabSize() {
    assert myTabSizeField instanceof IntegerField;
    return ((IntegerField)myTabSizeField).getValue();
  }

  public void apply(final CodeStyleSettings settings, CommonCodeStyleSettings.IndentOptions options) {
    options.INDENT_SIZE = getUIIndent();
    options.TAB_SIZE = getUITabSize();
    options.USE_TAB_CHARACTER = myCbUseTab.isSelected();
  }

  public void reset(@NotNull CodeStyleSettings settings, @NotNull CommonCodeStyleSettings.IndentOptions options) {
    ((IntegerField)myTabSizeField).setValue(options.TAB_SIZE);
    myCbUseTab.setSelected(options.USE_TAB_CHARACTER);

    ((IntegerField)myIndentField).setValue(options.INDENT_SIZE);
  }

  /**
   * @deprecated Create {@link IntegerField} and use {@link IntegerField#getValue()} instead.
   */
  @Deprecated(forRemoval = true)
  protected int getFieldValue(JTextField field, int minValue, int defValue) {
    if (field instanceof IntegerField) {
      return ((IntegerField)field).getValue();
    }
    else {
      try {
        return Math.max(Integer.parseInt(field.getText()), minValue);
      }
      catch (NumberFormatException e) {
        return defValue;
      }
    }
  }

  public void setEnabled(boolean enabled) {
    myIndentField.setEnabled(enabled);
    myIndentLabel.setEnabled(enabled);
    myTabSizeField.setEnabled(enabled);
    myTabSizeLabel.setEnabled(enabled);
    myCbUseTab.setEnabled(enabled);
  }

  protected void setVisible(boolean visible) {
    myIndentField.setVisible(visible);
    myIndentLabel.setVisible(visible);
    myTabSizeField.setVisible(visible);
    myTabSizeLabel.setVisible(visible);
    myCbUseTab.setVisible(visible);
  }

  private static @NlsContexts.Label String getIndentLabel() {
    return ApplicationBundle.message("editbox.indent.indent");
  }

  private static @NlsContexts.Label String getTabSizeLabel() {
    return ApplicationBundle.message("editbox.indent.tab.size");
  }

  protected final boolean isUseTabsSelected() {
    return myCbUseTab.isSelected();
  }
}
