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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class JavaIndentOptionsEditor extends SmartIndentOptionsEditor {
  private JTextField myLabelIndent;
  private JLabel myLabelIndentLabel;

  private JCheckBox myLabelIndentAbsolute;
  private JCheckBox myCbDontIndentTopLevelMembers;
  private JCheckBox myCbUseRelativeIndent;

  protected void addComponents() {
    super.addComponents();

    myLabelIndent = new JTextField(4);
    add(myLabelIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.label.indent")), myLabelIndent);

    myLabelIndentAbsolute = new JCheckBox(ApplicationBundle.message("checkbox.indent.absolute.label.indent"));
    add(myLabelIndentAbsolute, true);

    myCbDontIndentTopLevelMembers = new JCheckBox(ApplicationBundle.message("checkbox.do.not.indent.top.level.class.members"));
    add(myCbDontIndentTopLevelMembers);

    myCbUseRelativeIndent = new JCheckBox(ApplicationBundle.message("checkbox.use.relative.indents"));
    add(myCbUseRelativeIndent);
  }

  public boolean isModified(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
    boolean isModified = super.isModified(settings, options);
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);

    isModified |= isFieldModified(myLabelIndent, options.LABEL_INDENT_SIZE);
    isModified |= isFieldModified(myLabelIndentAbsolute, options.LABEL_INDENT_ABSOLUTE);
    isModified |= isFieldModified(myCbDontIndentTopLevelMembers, javaSettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS);
    isModified |= isFieldModified(myCbUseRelativeIndent, options.USE_RELATIVE_INDENTS);

    return isModified;
  }

  public void apply(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
    super.apply(settings, options);
    options.LABEL_INDENT_SIZE = getFieldValue(myLabelIndent, Integer.MIN_VALUE, options.LABEL_INDENT_SIZE);

    options.LABEL_INDENT_ABSOLUTE = myLabelIndentAbsolute.isSelected();
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = myCbDontIndentTopLevelMembers.isSelected();
    options.USE_RELATIVE_INDENTS = myCbUseRelativeIndent.isSelected();
  }

  public void reset(@NotNull final CodeStyleSettings settings, @NotNull final CommonCodeStyleSettings.IndentOptions options) {
    super.reset(settings, options);
    myLabelIndent.setText(Integer.toString(options.LABEL_INDENT_SIZE));
    myLabelIndentAbsolute.setSelected(options.LABEL_INDENT_ABSOLUTE);
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    myCbDontIndentTopLevelMembers.setSelected(javaSettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS);
    myCbUseRelativeIndent.setSelected(options.USE_RELATIVE_INDENTS);
  }

  public void setEnabled(final boolean enabled) {
    super.setEnabled(enabled);
    myLabelIndent.setEnabled(enabled);
    myLabelIndentLabel.setEnabled(enabled);
    myLabelIndentAbsolute.setEnabled(enabled);
  }
}
