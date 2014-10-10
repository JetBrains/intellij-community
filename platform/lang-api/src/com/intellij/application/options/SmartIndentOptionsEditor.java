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
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class SmartIndentOptionsEditor extends IndentOptionsEditor {
  private JCheckBox myCbSmartTabs;
  private JTextField myContinuationIndentField;
  private JLabel myContinuationIndentLabel;
  private JCheckBox myCbKeepIndentsOnEmptyLines;

  @Override
  protected void addTabOptions() {
    super.addTabOptions();

    myCbSmartTabs = new JCheckBox(ApplicationBundle.message("checkbox.indent.smart.tabs"));
    add(myCbSmartTabs, true);
  }

  @Override
  protected void addComponents() {
    super.addComponents();

    myContinuationIndentField = createIndentTextField();
    myContinuationIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.continuation.indent"));
    add(myContinuationIndentLabel, myContinuationIndentField);

    myCbKeepIndentsOnEmptyLines = new JCheckBox(ApplicationBundle.message("checkbox.indent.keep.indents.on.empty.lines"));
    add(myCbKeepIndentsOnEmptyLines);
  }

  @Override
  public boolean isModified(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
    boolean isModified = super.isModified(settings, options);
    isModified |= isFieldModified(myCbSmartTabs, options.SMART_TABS);
    isModified |= isFieldModified(myContinuationIndentField, options.CONTINUATION_INDENT_SIZE);
    isModified |= isFieldModified(myCbKeepIndentsOnEmptyLines, options.KEEP_INDENTS_ON_EMPTY_LINES);
    return isModified;
  }

  @Override
  public void apply(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
    super.apply(settings, options);
    options.CONTINUATION_INDENT_SIZE = getFieldValue(myContinuationIndentField, 0, options.CONTINUATION_INDENT_SIZE);
    options.SMART_TABS = isSmartTabValid(options.INDENT_SIZE, options.TAB_SIZE) && myCbSmartTabs.isSelected();
    options.KEEP_INDENTS_ON_EMPTY_LINES = myCbKeepIndentsOnEmptyLines.isSelected();
  }

  @Override
  public void reset(@NotNull final CodeStyleSettings settings, @NotNull final CommonCodeStyleSettings.IndentOptions options) {
    super.reset(settings, options);
    myContinuationIndentField.setText(String.valueOf(options.CONTINUATION_INDENT_SIZE));
    myCbSmartTabs.setSelected(options.SMART_TABS);
    myCbKeepIndentsOnEmptyLines.setSelected(options.KEEP_INDENTS_ON_EMPTY_LINES);
  }

  @Override
  public void setEnabled(final boolean enabled) {
    super.setEnabled(enabled);

    boolean smartTabsChecked = enabled && myCbUseTab.isSelected();
    boolean smartTabsValid = smartTabsChecked && isSmartTabValid(getUIIndent(), getUITabSize());
    myCbSmartTabs.setEnabled(smartTabsValid);
    myCbSmartTabs.setToolTipText(
      smartTabsChecked && !smartTabsValid ? ApplicationBundle.message("tooltip.indent.must.be.multiple.of.tab.size.for.smart.tabs.to.operate") : null);

    myContinuationIndentField.setEnabled(enabled);
    myContinuationIndentLabel.setEnabled(enabled);
  }

  private static boolean isSmartTabValid(int indent, int tabSize) {
    return (indent / tabSize) * tabSize == indent;
  }
}
